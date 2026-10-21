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

        CallNotificationHelper.cancelIncomingCallNotification(context);

        if (callId == null) return;

        if (CallNotificationHelper.ACTION_DECLINE_CALL.equals(action)) {
            CallManager.getInstance(context).rejectIncomingCall(callId);
        } else if (CallNotificationHelper.ACTION_ACCEPT_CALL.equals(action)) {
            CallManager.getInstance(context).acceptIncomingCall(context, callId);
        }
    }
}
