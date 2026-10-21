package com.insta.nova.calling;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.insta.nova.BuildConfig;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Supabase Realtime {@code postgres_changes} subscriber for {@code public.calls}.
 *
 * <p>Why this exists instead of a library: there is no Supabase Realtime client
 * for Java on Maven Central (neither {@code io.github.ugclee:realtime-java} nor
 * {@code io.github.supabase-community:realtime-postgres-kotlin} is published
 * there), so the Phoenix Channels protocol is spoken directly over OkHttp's
 * WebSocket, which the project already depends on.
 *
 * <p>Protocol, as implemented by supabase/realtime-js:
 * <ul>
 *   <li>Endpoint {@code wss://<project>/realtime/v1/websocket?apikey=<anon>&vsn=1.0.0},
 *       carrying {@code apikey} and {@code Authorization: Bearer <Firebase ID token>}
 *       headers. Supabase verifies the Firebase token through its third-party
 *       auth integration, which is what makes Realtime RLS evaluate
 *       {@code public.request_uid()}.</li>
 *   <li>Join channel {@code realtime:public:calls} with a {@code phx_join} whose
 *       payload carries {@code config.postgres_changes}.</li>
 *   <li>Heartbeat on topic {@code phoenix} every 25s; a missed reply forces a
 *       reconnect.</li>
 *   <li>Row events arrive as {@code event: "postgres_changes"} with
 *       {@code payload.data.{type,record,columns}}.</li>
 * </ul>
 *
 * <p>Delivery is RLS-scoped, so a device only ever receives rows where it is
 * the caller or the callee — exactly the subscription the spec asks for.
 */
public class CallRealtimeClient {

    private static final String TAG = "CallRealtime";

    private static final String TOPIC_CALLS = "realtime:public:calls";
    private static final String TOPIC_PHOENIX = "phoenix";
    private static final long HEARTBEAT_INTERVAL_MS = 25_000L;
    private static final long JOIN_TIMEOUT_MS = 10_000L;

    /** Stepped reconnect backoff, matching realtime-js. */
    private static final long[] RECONNECT_BACKOFF_MS = {1_000L, 2_000L, 5_000L, 10_000L};

    public interface Listener {
        /** A row in {@code calls} was inserted, updated or deleted. */
        void onCallRow(@NonNull String eventType, @NonNull CallRecord record);

        /** Socket lifecycle, for diagnostics and for the logcat checks. */
        void onRealtimeState(@NonNull String state, @Nullable String detail);
    }

    private static CallRealtimeClient instance;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient httpClient;

    private volatile WebSocket socket;
    private volatile boolean wantConnection = false;
    private volatile String accessToken;
    private volatile int reconnectAttempt = 0;

    private String joinRef;
    private int refCounter = 0;
    private int reconnectTries = 0;

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (socket == null || wantConnection) {
                sendHeartbeat();
                main.postDelayed(this, HEARTBEAT_INTERVAL_MS);
            }
        }
    };

    private volatile Listener listener;

    public static synchronized CallRealtimeClient getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new CallRealtimeClient(context.getApplicationContext());
        }
        return instance;
    }

    private CallRealtimeClient(Context context) {
        this.context = context;

        // A dedicated client: the shared Retrofit client installs the calling
        // fallback interceptor, which rewrites call-related requests to a LAN
        // relay on a 404. That rewriting must not apply to the socket.
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                // Realtime servers send pings and leave the socket idle between
                // events; a read timeout would kill a healthy connection.
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        return socket != null && wantConnection;
    }

    /**
     * Opens (or re-authenticates) the subscription.
     *
     * <p>Called from {@code InstaApp.onCreate}, where FirebaseAuth may not have
     * produced a token yet. If the socket therefore joins anonymously, Realtime
     * evaluates RLS with a null identity and silently delivers <em>zero</em>
     * rows — the channel reports "joined" while no call event ever arrives. So
     * a later call with a real token must re-authenticate the live socket
     * rather than being ignored because it is already open.
     */
    public synchronized void connect(@Nullable String token) {
        String previous = this.accessToken;
        this.accessToken = token;
        this.wantConnection = true;

        if (socket == null) {
            openSocket();
            return;
        }
        boolean gained = (previous == null || previous.isEmpty())
                && token != null && !token.isEmpty();
        boolean rotated = token != null && !token.equals(previous);
        if (gained || rotated) {
            Log.i(TAG, "re-authenticating live socket for realtime RLS");
            updateToken(token);
        }
    }

    /** Pushes a refreshed token into the live socket without dropping it. */
    public synchronized void updateToken(@Nullable String token) {
        this.accessToken = token;
        if (socket == null) return;
        // Matches RealtimeClient._performAuth: an already-joined channel gets an
        // access_token push rather than a full re-join.
        JsonObject body = json();
        body.addProperty("access_token", token);
        // join_ref must be present on a channel push, or the server treats it
        // as a socket-level message and ignores it.
        send(TOPIC_CALLS, "access_token", body, joinRef);
    }

    public synchronized void disconnect() {
        wantConnection = false;
        main.removeCallbacks(heartbeatRunnable);
        WebSocket s = socket;
        socket = null;
        if (s != null) s.close(1000, "client disconnect");
        notifyState("disconnected", null);
    }

    private void openSocket() {
        String base = BuildConfig.SUPABASE_URL;
        if (base == null || base.trim().isEmpty()) {
            Log.w(TAG, "SUPABASE_URL is empty; realtime disabled");
            return;
        }
        // Scheme mapping must replace the WHOLE scheme token. A naive
        // replaceFirst("^http", "wss") turns "https" into "wsss", which OkHttp
        // rejects outright (IllegalArgumentException: Expected URL scheme
        // 'http' or 'https' but was 'wsss') and crashes the app on launch.
        String trimmed = base.trim();
        String host;
        if (trimmed.startsWith("https://")) {
            host = "wss://" + trimmed.substring("https://".length());
        } else if (trimmed.startsWith("http://")) {
            host = "ws://" + trimmed.substring("http://".length());
        } else {
            host = trimmed;
        }
        // Trailing slashes would produce a double slash before the path.
        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        String wsUrl = host + "/realtime/v1/websocket?vsn=1.0.0&apikey="
                + BuildConfig.SUPABASE_ANON_KEY.trim();

        // Never fall back to FirebaseTokens.current() here: both of these run on
        // the main thread, and its Tasks.await throws "Must not be called on the
        // main application thread", yielding a null token and an anonymous
        // join whose RLS silently drops every row. CallManager supplies the
        // token through the async path instead.
        String token = accessToken;

        // Realtime is an enhancement over the existing polling/WebRTC path, so
        // a failure here must never propagate: this is called from
        // InstaApp.onCreate, where an exception takes the whole process down
        // before any Activity exists.
        try {
            Request.Builder rb = new Request.Builder().url(wsUrl);
            rb.header("apikey", BuildConfig.SUPABASE_ANON_KEY.trim());
            if (token != null && !token.isEmpty()) {
                rb.header("Authorization", "Bearer " + token);
            }

            notifyState("connecting", null);

            socket = httpClient.newWebSocket(rb.build(), new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                Log.i(TAG, "socket open");
                main.post(() -> {
                    reconnectTries = 0;
                    main.removeCallbacks(heartbeatRunnable);
                    main.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS);
                    joinCallsChannel();
                });
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                handleMessage(text);
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t,
                                  @Nullable Response response) {
                Log.w(TAG, "socket failure: " + t.getMessage());
                scheduleReconnect("failure: " + t.getMessage());
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                Log.i(TAG, "socket closed: " + code + " " + reason);
                scheduleReconnect("closed: " + code + " " + reason);
            }
            });
        } catch (Exception e) {
            // Malformed URL, blocked socket, missing key — log and carry on.
            // The call screens still work through the legacy signalling path.
            Log.e(TAG, "could not open realtime socket: " + e.getMessage());
            socket = null;
            notifyState("error", e.getMessage());
        }
    }

    // ---------------------------------------------------------------- joining

    private void joinCallsChannel() {
        joinRef = nextRef();

        // Both INSERT and UPDATE are needed: INSERT is the incoming-call signal,
        // UPDATE carries every subsequent state transition for both sides.
        StringBuilder filters = new StringBuilder();
        filters.append("[{\"event\":\"INSERT\",\"schema\":\"public\",\"table\":\"calls\"},")
                .append("{\"event\":\"UPDATE\",\"schema\":\"public\",\"table\":\"calls\"},")
                .append("{\"event\":\"DELETE\",\"schema\":\"public\",\"table\":\"calls\"}]");

        JsonObject config = json();

        JsonObject broadcast = json();
        broadcast.addProperty("ack", false);
        broadcast.addProperty("self", false);
        config.add("broadcast", broadcast);

        JsonObject presence = json();
        presence.addProperty("key", "");
        presence.addProperty("enabled", false);
        config.add("presence", presence);

        config.addProperty("private", false);
        config.add("postgres_changes", JsonParser.parseString(filters.toString()).getAsJsonArray());

        JsonObject payload = json();
        payload.add("config", config);
        // Never fall back to FirebaseTokens.current() here: both of these run on
        // the main thread, and its Tasks.await throws "Must not be called on the
        // main application thread", yielding a null token and an anonymous
        // join whose RLS silently drops every row. CallManager supplies the
        // token through the async path instead.
        String token = accessToken;
        if (token != null) payload.addProperty("access_token", token);

        send(TOPIC_CALLS, "phx_join", payload, joinRef);

        main.postDelayed(() -> {
            if (joinRef != null && !joinRef.isEmpty() && !"joined".equals(joinState)) {
                Log.w(TAG, "join not acknowledged within " + JOIN_TIMEOUT_MS + "ms");
                scheduleReconnect("join timeout");
            }
        }, JOIN_TIMEOUT_MS);
    }

    private volatile String joinState = null;

    // ------------------------------------------------------------- transport

    private void sendHeartbeat() {
        send(TOPIC_PHOENIX, "heartbeat", json(), null);
    }

    /**
     * Sends one Phoenix frame.
     *
     * <p>{@code payload} is attached as a nested JSON <em>object</em>, not as a
     * string. Encoding it as a string would make the server see
     * {@code "payload": "{}"} as text and silently drop the join config.
     */
    private void send(String topic, String event, JsonObject payload, String joinRefOrNull) {
        WebSocket s = socket;
        if (s == null) return;
        JsonObject msg = json();
        msg.addProperty("topic", topic);
        msg.addProperty("event", event);
        msg.add("payload", payload == null ? json() : payload);
        msg.addProperty("ref", nextRef());
        if (joinRefOrNull != null) msg.addProperty("join_ref", joinRefOrNull);
        String out = msg.toString();
        // Per-frame payloads are debug-only. Keep socket failures, lifecycle,
        // join errors and call state transitions visible at info/warn/error.
        Log.d(TAG, "TX event=" + event + " topic=" + topic);
        s.send(out);
    }

    private synchronized String nextRef() {
        refCounter++;
        return String.valueOf(refCounter);
    }

    // -------------------------------------------------------------- inbound

    private void handleMessage(String text) {
        // Per-frame payloads are debug-only; lifecycle and subscription errors
        // remain logged below at info/warn/error.
        Log.d(TAG, "RX frame");
        try {
            JsonObject msg = JsonParser.parseString(text).getAsJsonObject();
            String topic = optString(msg, "topic");
            String event = optString(msg, "event");
            JsonObject payload = msg.has("payload") && msg.get("payload").isJsonObject()
                    ? msg.getAsJsonObject("payload") : new JsonObject();

            if (TOPIC_PHOENIX.equals(topic) && "phx_reply".equals(event)) {
                return; // heartbeat ack, nothing to do
            }

            if ("postgres_changes".equals(event)) {
                dispatchChange(payload);
                return;
            }

            if ("phx_reply".equals(event)) {
                String status = payload.has("status") ? payload.get("status").getAsString() : "";
                if (TOPIC_CALLS.equals(topic)) {
                    if ("ok".equals(status)) {
                        joinState = "joined";
                        notifyState("joined", null);
                    } else {
                        joinState = status;
                        Log.w(TAG, "join rejected: " + payload);
                        notifyState("error", status);
                    }
                }
                return;
            }

            if ("phx_error".equals(event)) {
                Log.w(TAG, "phx_error on " + topic + ": " + payload);
                notifyState("error", payload.toString());
            }
        } catch (Exception e) {
            Log.w(TAG, "bad realtime frame: " + e.getMessage());
        }
    }

    private void dispatchChange(JsonObject payload) {
        if (!payload.has("data") || !payload.get("data").isJsonObject()) return;
        JsonObject data = payload.getAsJsonObject("data");

        String type = optString(data, "type");
        JsonObject recordObj = data.has("record") && data.get("record").isJsonObject()
                ? data.getAsJsonObject("record") : new JsonObject();

        // Postgres has no `new`/`old` in the raw frame; realtime-js synthesises
        // them from record/old_record. Only the new row is used here.
        CallRecord record;
        try {
            record = CallRecord.fromJson(new JSONObject(recordObj.toString()));
        } catch (Exception e) {
            Log.w(TAG, "could not decode change record: " + e.getMessage());
            return;
        }

        Log.i(TAG, "change " + type + " -> " + record);
        main.post(() -> {
            Listener l = listener;
            if (l != null) {
                try {
                    l.onCallRow(type == null ? "UPDATE" : type, record);
                } catch (Exception e) {
                    Log.e(TAG, "listener threw: " + e.getMessage());
                }
            }
        });
    }

    // ------------------------------------------------------------ reconnect

    private void scheduleReconnect(String reason) {
        if (!wantConnection) return;
        socket = null;
        main.removeCallbacks(heartbeatRunnable);
        joinState = null;
        notifyState("reconnecting", reason);

        long delay = RECONNECT_BACKOFF_MS[Math.min(reconnectTries, RECONNECT_BACKOFF_MS.length - 1)];
        reconnectTries++;
        main.postDelayed(() -> {
            if (wantConnection && socket == null) openSocket();
        }, delay);
    }

    private void notifyState(String state, @Nullable String detail) {
        main.post(() -> {
            Listener l = listener;
            if (l != null) {
                try {
                    l.onRealtimeState(state, detail);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static JsonObject json() {
        return new JsonObject();
    }

    private static String optString(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}