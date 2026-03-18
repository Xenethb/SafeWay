package com.s23010602.safeway;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;

/**
 * PRODUCTION READY: The trigger point for "Safe Mode".
 * Handles service orchestration and ensures automatic cleanup after a set duration.
 */
public class ActivationReceiver extends BroadcastReceiver {
    private static final String TAG = "ActivationReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Guardian Mode Triggered");

        // 1. Safety Check: If the user logged out, don't start tracking them!
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.w(TAG, "Activation aborted: No user logged in.");
            return;
        }

        // 2. Start Services
        startForegroundService(context, LiveLocationService.class);
        startForegroundService(context, QuickSosService.class);

        // 3. Schedule the "Kill Switch"
        scheduleStopAlarm(context);

        Toast.makeText(context, "Safeway: Guardian Mode Activated", Toast.LENGTH_LONG).show();
    }

    /**
     * Helper to start foreground services correctly across all Android versions.
     */
    private void startForegroundService(Context context, Class<?> serviceClass) {
        try {
            Intent intent = new Intent(context, serviceClass);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            Log.d(TAG, "Successfully started: " + serviceClass.getSimpleName());
        } catch (Exception e) {
            Log.e(TAG, "Failed to start " + serviceClass.getSimpleName(), e);
        }
    }

    /**
     * Calculates duration and schedules the StopReceiver to clean up.
     */
    private void scheduleStopAlarm(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("activation_settings", Context.MODE_PRIVATE);

        // duration index (0 = 1hr, 1 = 2hr, etc.)
        int durationIdx = prefs.getInt("duration", 0);
        long durationMs = (durationIdx + 1) * 3600000L;
        long stopAt = System.currentTimeMillis() + durationMs;

        Intent stopIntent = new Intent(context, StopReceiver.class);
        PendingIntent piStop = PendingIntent.getBroadcast(
                context,
                999, // Use a unique ID for the stop alarm
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            try {
                // We use setExactAndAllowWhileIdle because if the user is in trouble,
                // the "Stop" timer must be precise even if the phone is saving battery.
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, stopAt, piStop);
                Log.d(TAG, "Scheduled automatic stop in " + (durationIdx + 1) + " hour(s).");
            } catch (SecurityException se) {
                // Fallback for cases where exact alarm permission is missing
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, stopAt, piStop);
                Log.w(TAG, "Using inexact alarm due to missing permissions.");
            }
        }
    }
}