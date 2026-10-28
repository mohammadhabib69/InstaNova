package com.insta.nova.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

/**
 * Where a notification tap should land (task P7-10).
 *
 * <p>A pure mapping from an FCM data payload to a destination, deliberately free of Android
 * types so it can be unit-tested. The alternative — deciding the destination inside an
 * {@code Activity} on a device, by tapping a real notification — is the one part of this
 * project that cannot be automated, so the decision is extracted where a test can reach it.
 *
 * <p><b>Not wired yet, on purpose.</b> Nothing sends a like/comment/follow notification
 * today: dispatch needs a Cloud Function (P7-06, blocked on billing), so the only FCM
 * messages that arrive are call-related and already handled in
 * {@code InstaFirebaseMessagingService}. Wiring a tap handler before a sender exists would
 * be untestable code with no way to observe it working. The contract is fixed here so the
 * dispatcher and the tap handler cannot disagree about payload field names — the usual
 * failure in this kind of feature is a sender writing {@code postId} while the receiver
 * reads {@code post_id}.
 */
public final class NotificationRoute {

    public enum Kind {
        /** A post detail screen. */
        POST,
        /** Another user's profile. */
        PROFILE,
        /** A specific conversation. */
        CHAT,
        /** The incoming-call screen for a call id. */
        CALL,
        /** The notifications tab — the safe fallback for anything unrecognised. */
        NOTIFICATIONS,
        /** Nothing to open (e.g. the app is already showing the target). */
        NONE
    }

    /** Payload keys owned by the dispatcher. Changing one of these is a wire-format change. */
    public static final String KEY_TYPE = "type";
    public static final String KEY_POST_ID = "post_id";
    public static final String KEY_ACTOR_UID = "actor_uid";
    public static final String KEY_CONVERSATION_ID = "conversation_id";
    public static final String KEY_CALL_ID = "call_id";

    public final Kind kind;
    @Nullable public final String id;
    /** True when the tap must open a new task rather than reuse a running one. */
    public final boolean newTask;

    private NotificationRoute(Kind kind, @Nullable String id) {
        this.kind = kind;
        this.id = id;
        // Deep-linking into a specific post or profile from a cold start must not reuse a
        // stale task stack, or back lands somewhere unrelated.
        this.newTask = kind == Kind.POST || kind == Kind.PROFILE
                || kind == Kind.CHAT || kind == Kind.CALL;
    }

    private static final NotificationRoute HUB = new NotificationRoute(Kind.NOTIFICATIONS, null);
    private static final NotificationRoute NOTHING = new NotificationRoute(Kind.NONE, null);

    /**
     * Resolve a data payload. Unknown or incomplete payloads fall back to the notifications
     * tab rather than throwing or opening the wrong screen: a tap must always do something
     * sensible, even if the payload is from an older app version.
     */
    @NonNull
    public static NotificationRoute forPayload(@Nullable Map<String, String> data) {
        if (data == null || data.isEmpty()) return NOTHING;

        String type = trim(data.get(KEY_TYPE));
        if (type.isEmpty()) return HUB;

        switch (type) {
            case "like":
            case "comment":
            case "mention":
            case "comment_like": {
                String postId = trim(data.get(KEY_POST_ID));
                return postId.isEmpty() ? HUB : new NotificationRoute(Kind.POST, postId);
            }
            case "follow":
            case "follow_request":
            case "follow_accepted": {
                String uid = trim(data.get(KEY_ACTOR_UID));
                return uid.isEmpty() ? HUB : new NotificationRoute(Kind.PROFILE, uid);
            }
            case "message": {
                String cid = trim(data.get(KEY_CONVERSATION_ID));
                return cid.isEmpty() ? HUB : new NotificationRoute(Kind.CHAT, cid);
            }
            case "incoming_call": {
                String callId = trim(data.get(KEY_CALL_ID));
                return callId.isEmpty() ? HUB : new NotificationRoute(Kind.CALL, callId);
            }
            case "call_ended":
            case "call_cancelled":
                // A terminal call event is a dismiss signal, never a navigation target.
                return NOTHING;
            default:
                return HUB;
        }
    }

    @NonNull
    private static String trim(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    public String toString() {
        return kind + (id == null ? "" : ":" + id);
    }
}
