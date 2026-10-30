package com.insta.nova.calling;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;

/**
 * Handles Accept and Decline actions directly from notification action buttons.
 */
public class CallActionReceiver extends BroadcastReceiver {

    private static final String TAG = "CallActionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        String callId = intent.getStringExtra(IncomingCallActivity.EXTRA_CALL_ID);
        Log.d(TAG, "onReceive: " + action + " for callId: " + callId);

        // Any button pressed on the ringing card ends the ring, whichever action it is.
        CallRingtone.get().stop();
        CallNotificationHelper.cancelIncomingCallNotification(context);

        if (callId == null) return;

        if (CallNotificationHelper.ACTION_DECLINE_CALL.equals(action)) {
            CallManager.getInstance(context).rejectIncomingCall(callId);
        } else if (CallNotificationHelper.ACTION_ACCEPT_CALL.equals(action)) {
            CallManager.getInstance(context).acceptIncomingCall(context, callId);
        } else if (CallNotificationHelper.ACTION_END_CALL.equals(action)) {
            // "End call" on the in-progress chip: same path as hanging up on screen,
            // with the duration derived from the row's own connected_at so the history
            // entry is not written as a zero-second call.
            CallManager manager = CallManager.getInstance(context);
            CallRecord active = manager.getActiveCall();
            int duration = 0;
            if (active != null && callId.equals(active.id) && active.connectedAtMillis() > 0) {
                duration = (int) Math.max(0,
                        (System.currentTimeMillis() - active.connectedAtMillis()) / 1000L);
            }
            manager.endCall(callId, duration);
            CallNotificationHelper.cancelOngoingCallNotification(context);
        }
    }
}
