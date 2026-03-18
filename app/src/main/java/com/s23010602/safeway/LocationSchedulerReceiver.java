package com.s23010602.safeway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;
import java.util.List;

/**
 * LocationSchedulerReceiver is a BroadcastReceiver responsible for starting and stopping
 * the LiveLocationService based on user-defined activation settings.
 *
 * Reason for this class:
 * - To enable automatic time-based location sharing without requiring the user
 *   to manually start the service.
 * - Can be triggered by system alarms (AlarmManager) at scheduled times.
 */
public class LocationSchedulerReceiver extends BroadcastReceiver {

    /**
     * This method is called when the BroadcastReceiver receives an Intent.
     * It checks the user's activation settings in Firebase and starts/stops
     * LiveLocationService accordingly.
     *
     * @param context The context from which the receiver was triggered
     * @param intent The intent that triggered the receiver
     */
    @Override
    public void onReceive(Context context, Intent intent) {

        // Get the currently signed-in Firebase user
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return; // exit if no user is logged in

        // Fetch the user's activation settings from Firebase Realtime Database
        FirebaseDatabase.getInstance()
                .getReference("users").child(user.getUid())
                .child("activation_settings")
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    /**
                     * Callback when the data is successfully fetched
                     */
                    @Override
                    public void onDataChange(DataSnapshot snap) {

                        // Check if time-based activation is enabled
                        Boolean tb = snap.child("timeBased").getValue(Boolean.class);
                        if (tb == null || !tb) return;

                        // Get the list of selected days (e.g., ["Mo","Tu","We"])
                        List<String> days = (List<String>) snap.child("days").getValue();
                        if (days == null) return;

                        // Array to map Calendar.DAY_OF_WEEK to short codes
                        String[] codes = {"Su","Mo","Tu","We","Th","Fr","Sa"};
                        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1;

                        // Exit if today is not a selected day
                        if (!days.contains(codes[dow])) return;

                        // Start LiveLocationService
                        context.startService(new Intent(context, LiveLocationService.class));

                        // If a duration is set, schedule stopping the service after that duration
                        Integer dur = snap.child("duration").getValue(Integer.class);
                        if (dur != null) {
                            new Handler().postDelayed(() ->
                                            context.stopService(new Intent(context, LiveLocationService.class)),
                                    (dur + 1) * 60L * 60L * 1000L); // duration in milliseconds
                        }
                    }

                    /**
                     * Callback if the database read is cancelled or fails
                     */
                    @Override
                    public void onCancelled(DatabaseError error) {
                        // Can log error if needed
                    }
                });
    }
}
