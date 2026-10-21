package com.insta.nova.calling;

import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles incoming Firebase Cloud Messaging pushes for incoming calls
 * and token refreshes.
 */
public class InstaFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "InstaFCM";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.i(TAG, "New FCM Token received: " + token.substring(0, Math.min(token.length(), 10)) + "...");
        registerTokenWithBackend(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.i(TAG, "FCM Message received from: " + remoteMessage.getFrom());

        Map<String, String> data = remoteMessage.getData();
        if (data.isEmpty()) return;

        Log.i(TAG, "FCM Message data payload: type=" + data.get("type") + ", callId=" + data.get("call_id"));

        String type = data.get("type");
        String callId = data.get("call_id");

        if ("call_cancelled".equals(type) || "call_ended".equals(type)) {
            CallNotificationHelper.cancelIncomingCallNotification(this);
            if (callId != null) {
                LocalSignalingBus.getInstance().postCallEnded(callId, type);
            }
            return;
        }

        if ("incoming_call".equals(type) && callId != null) {
            String callType = data.get("call_type");
            String callerId = data.get("caller_id");
            // send-call-push sends caller_name; the older dispatcher sent
            // caller_username. Accept both so either function works.
            String callerUsername = data.get("caller_name") != null
                    ? data.get("caller_name") : data.get("caller_username");
            String callerAvatar = data.get("caller_avatar");
            String conversationId = data.get("conversation_id");

            // Guard: this device must never treat its own outgoing call as an
            // incoming one. A malformed or duplicated push must not raise an
            // IncomingCallActivity on top of the caller's own live call.
            String myId = SessionManager.getInstance(this).getUserId();
            if (myId != null && !myId.isEmpty() && myId.equals(callerId)) {
                Log.i(TAG, "incoming_call push is for our own outgoing call; ignoring");
                return;
            }

            CallSession session = new CallSession();
            session.callId = callId;
            session.conversationId = conversationId;
            session.callerId = callerId;
            session.callerUsername = callerUsername;
            session.callerAvatar = callerAvatar;
            session.callType = callType != null ? callType : CallSession.TYPE_AUDIO;
            session.recipientId = SessionManager.getInstance(this).getUserId();
            session.isOutgoing = false;

            // De-duplicate by call_id. When this process is alive the same call
            // also arrives over Realtime (and, for the legacy path, the 3s poll);
            // claimIncomingCall guarantees the incoming screen and the ringing
            // notification each appear exactly once.
            CallManager manager = CallManager.getInstance(this);
            if (!manager.claimIncomingCall(callId)) {
                Log.i(TAG, "FCM for " + callId + " already handled; ignoring duplicate");
                return;
            }

            // Post the high-priority/full-screen notification before handing off
            // to the Activity. This is the display boundary for a killed or
            // backgrounded process, so ACK from this service path as well as from
            // IncomingCallActivity. The database CAS makes the second ACK a no-op.
            CallNotificationHelper.showIncomingCallNotification(this, session);
            manager.acknowledgeRinging(callId);
            manager.onIncomingCallReceived(session);
            manager.startRealtime();
        }
    }

    public static void registerTokenWithBackend(@NonNull android.content.Context context) {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> registerTokenWithBackend(context, token))
                .addOnFailureListener(e -> Log.w(TAG, "Failed to get FCM token: " + e.getMessage()));
    }

    public static void registerTokenWithBackend(@NonNull android.content.Context context, @NonNull String token) {
        String myUserId = SessionManager.getInstance(context).getUserId();
        if (myUserId == null || myUserId.isEmpty()) return;

        RestApi restApi = RetrofitClient.getInstance(context).getRestApi();

            // Prompt K: send-call-push reads profiles.fcm_token directly.
            // The legacy dispatcher is no longer part of the client call path.
            Map<String, Object> profileBody = new HashMap<>();
        profileBody.put("fcm_token", token);
        restApi.updateOwnFcmToken("eq." + myUserId, profileBody).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.i(TAG, "profiles.fcm_token updated");
                } else {
                    // 400 here usually means the Prompt J migration has not been
                    // applied yet, or the column grant is missing.
                    Log.w(TAG, "profiles.fcm_token update HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Log.w(TAG, "profiles.fcm_token update failed: " + t.getMessage());
            }
        });

        Map<String, Object> body = new HashMap<>();
        body.put("user_id", myUserId);
        body.put("fcm_token", token);
        body.put("device_model", Build.MANUFACTURER + " " + Build.MODEL);
        body.put("platform", "android");

        restApi.registerDeviceToken(body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.i(TAG, "FCM token registered with backend successfully");
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Log.w(TAG, "Failed to register FCM token with backend: " + t.getMessage());
            }
        });
    }

    private void registerTokenWithBackend(String token) {
        registerTokenWithBackend(this, token);
    }
}
