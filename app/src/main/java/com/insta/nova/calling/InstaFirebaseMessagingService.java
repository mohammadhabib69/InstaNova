package com.insta.nova.calling;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.insta.nova.R;
import com.insta.nova.data.local.AppSettingsStore;
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
import java.util.List;
import java.util.Map;

/**
 * Handles incoming Firebase Cloud Messaging pushes for incoming calls
 * and token refreshes.
 */
public class InstaFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "InstaFCM";

    /** Activity notifications: reactions, follows. Separate from the call channels. */
    private static final String CHANNEL_ACTIVITY = "instanova_activity_v1";

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

        // Activity rows written by the backend. These are pushed by the
        // send-notification-push Edge Function, triggered on insert, so they arrive
        // even when the app process is dead — which is the only way a reaction to a
        // story reaches someone who is not looking at the screen.
        if (type != null && !type.isEmpty() && !"incoming_call".equals(type)
                && !"call_cancelled".equals(type) && !"call_ended".equals(type)) {
            showActivityNotification(type, data);
            return;
        }

        if ("incoming_call".equals(type) && callId != null) {
            final String incomingCallId = callId;
            String callerId = data.get("caller_id");
            if (callerId == null || callerId.isEmpty()) return;

            com.insta.nova.data.local.AppSettingsStore settings =
                    new com.insta.nova.data.local.AppSettingsStore(this);

            // Call notifications switch gates the push itself: no notification,
            // no ring, no IncomingCallActivity.
            if (!settings.allowsNotificationType("call")) {
                Log.i(TAG, "call notifications disabled; dropping incoming call");
                return;
            }
            if (new com.insta.nova.data.local.RelationshipStore(this).isBlocked(callerId)) {
                Log.i(TAG, "caller is blocked; dropping incoming call");
                return;
            }

            String who = settings.whoCanCall();
            if (com.insta.nova.data.local.AppSettingsStore.WHO_NOBODY.equals(who)) {
                Log.i(TAG, "who_can_call=nobody; dropping incoming call");
                return;
            }
            if (com.insta.nova.data.local.AppSettingsStore.WHO_FOLLOWING.equals(who)) {
                // Only people I follow may ring me. Fail closed while checking.
                String myId = SessionManager.getInstance(this).getUserId();
                Map<String, String> q = new HashMap<>();
                q.put("follower_id", "eq." + myId);
                q.put("following_id", "eq." + callerId);
                q.put("select", "following_id");
                RetrofitClient.getInstance(this).getRestApi().getFollows(q)
                        .enqueue(new Callback<List<Map<String, Object>>>() {
                            @Override
                            public void onResponse(Call<List<Map<String, Object>>> call,
                                                   Response<List<Map<String, Object>>> response) {
                                if (response.isSuccessful() && response.body() != null
                                        && !response.body().isEmpty()) {
                                    deliverIncoming(data, incomingCallId);
                                } else {
                                    Log.i(TAG, "caller not followed; dropping incoming call");
                                }
                            }

                            @Override
                            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                                Log.i(TAG, "follow check failed; dropping incoming call");
                            }
                        });
                return;
            }
            deliverIncoming(data, incomingCallId);
        }
    }

    /** Raises the ringing notification and incoming screen for an allowed call. */
    private void deliverIncoming(Map<String, String> data, String callId) {
        String callType = data.get("call_type");
        String callerId = data.get("caller_id");
        String callerUsername = data.get("caller_name") != null
                ? data.get("caller_name") : data.get("caller_username");
        String callerAvatar = data.get("caller_avatar");
        String conversationId = data.get("conversation_id");

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
        session.recipientId = myId;
        session.isOutgoing = false;

        CallManager manager = CallManager.getInstance(this);
        if (!manager.claimIncomingCall(callId)) {
            Log.i(TAG, "FCM for " + callId + " already handled; ignoring duplicate");
            return;
        }
        CallNotificationHelper.showIncomingCallNotification(this, session);
        manager.acknowledgeRinging(callId);
        manager.onIncomingCallReceived(session);
        manager.startRealtime();
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

    /**
     * Presents a backend activity row (story reaction, follow) as a system
     * notification. Data-only pushes leave the decision here, which keeps the user's
     * per-type switches meaningful: a disabled switch drops the push silently.
     */
    private void showActivityNotification(String type, Map<String, String> data) {
        AppSettingsStore settings = new AppSettingsStore(this);
        if (!settings.allowsNotificationType(type)) {
            Log.i(TAG, "notification type " + type + " disabled; dropping push");
            return;
        }

        String actorName = data.get("actor_name");
        if (actorName == null || actorName.isEmpty()) actorName = "Someone";
        String body = data.get("body");

        String text;
        if ("story_reaction".equals(type)) {
            text = getString(R.string.notification_reacted_story);
            if (body != null && !body.isEmpty()) text = text + " " + body;
        } else if (type.startsWith("follow")) {
            text = "started following you";
        } else {
            return;
        }

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ACTIVITY, "Activity", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Story reactions, follows and other account activity");
            nm.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, com.insta.nova.ui.activity.NotificationsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, flags);

        String notificationId = data.get("notification_id");
        int id = notificationId == null ? (int) System.currentTimeMillis()
                : Math.abs(notificationId.hashCode());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ACTIVITY)
                .setSmallIcon(R.drawable.ic_logo_mark_mono)
                .setContentTitle(actorName)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        if (!manager.areNotificationsEnabled()) {
            // notify() is a silent no-op in this state — no exception, nothing shown.
            // Say so, otherwise a delivered push looks like a delivery failure.
            Log.w(TAG, "notifications disabled for the app; push for " + type + " not shown");
            return;
        }
        try {
            manager.notify(id, builder.build());
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS revoked between the check and the call.
            Log.w(TAG, "notification permission missing, push not shown", e);
        }
    }
}
