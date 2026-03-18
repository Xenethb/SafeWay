package com.s23010602.safeway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

/**
 * PRODUCTION READY: The cleanup mechanism.
 * Orchestrates the shutdown of all active safety services.
 */
public class StopReceiver extends BroadcastReceiver {
    private static final String TAG = "StopReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Guardian Mode: Initiating Shutdown...");

        // 1. Stop QuickSosService via Action
        // We use an Action so the service can gracefully unregister Shake/Power listeners
        try {
            Intent cancelSos = new Intent(context, QuickSosService.class);
            cancelSos.setAction(QuickSosService.ACTION_CANCEL);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(cancelSos);
            } else {
                context.startService(cancelSos);
            }
        } catch (Exception e) {
            Log.w(TAG, "QuickSosService already stopped or inaccessible.");
        }

        // 2. Stop LiveLocationService
        try {
            context.stopService(new Intent(context, LiveLocationService.class));
        } catch (Exception e) {
            Log.w(TAG, "LiveLocationService stop failed: " + e.getMessage());
        }

        // 3. Cleanup Notifications
        // This ensures the "Guardian Active" or "SOS Recording" icons disappear
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        nm.cancel(QuickSosService.NOTIF_ID);
        // Also cancel the LiveLocation notification ID if it's different
        nm.cancel(1);

        Toast.makeText(context, "Safeway: Safety monitoring deactivated", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "StopReceiver: Services terminated successfully.");
    }
}