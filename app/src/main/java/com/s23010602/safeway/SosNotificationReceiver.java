package com.s23010602.safeway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;
import android.util.Log;

/**
 * SosNotificationReceiver:
 * Listens for broadcast intents sent from SOS notifications (e.g., Cancel button).
 * Handles user actions directly from notifications without opening the app UI.
 */
public class SosNotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "SosNotificationReceiver"; // Tag for log messages

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return; // Defensive check: ignore null intents

        String action = intent.getAction(); // Get the action from the broadcast
        Log.d(TAG, "onReceive action=" + action);

        // Cancel action from notification -> stop the QuickSosService and remove notification
        if (QuickSosService.ACTION_CANCEL.equals(action)) {
            try {
                // stop service (will trigger onDestroy where service removes/stops foreground)
                context.stopService(new Intent(context, QuickSosService.class));
            } catch (Exception ex) {
                Log.w(TAG, "Failed to stop QuickSosService: " + ex.getMessage());
            }

            try {
                // Cancel the active notification related to QuickSosService
                NotificationManagerCompat.from(context).cancel(QuickSosService.NOTIF_ID);
            } catch (Exception ex) {
                Log.w(TAG, "Failed to cancel notification: " + ex.getMessage());
            }
        }
    }
}
