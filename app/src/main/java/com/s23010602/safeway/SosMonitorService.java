package com.s23010602.safeway;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * PRODUCTION READY: High-priority monitor for incoming SOS signals.
 * Optimized for Android 14+ with critical notification support.
 */
public class SosMonitorService extends Service {
    private static final String TAG = "SosMonitorService";
    private static final String CHANNEL_ID = "monitor_sos_channel";
    private static final int FOREGROUND_ID = 999;

    private DatabaseReference sosAlertsRef;
    private ChildEventListener sosListener;
    private String currentUid;
    private final LinkedHashSet<String> seenIds = new LinkedHashSet<>();
    private final int MAX_SEEN = 300;
    private volatile boolean initialLoadDone = false;

    @Override
    public void onCreate() {
        super.onCreate();
        currentUid = (FirebaseAuth.getInstance().getCurrentUser() != null)
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUid == null) {
            stopSelf();
            return;
        }

        createNotificationChannel();
        startGuardianForeground();
        startListeningForAlerts();
    }

    private void startGuardianForeground() {
        // This notification tells the user that Safeway is watching out for them.
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sos)
                .setContentTitle("Safeway Guardian Active")
                .setContentText("Monitoring for emergency alerts from friends...")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        // Android 14 (API 34) strict foreground service enforcement
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(FOREGROUND_ID, notification);
        }
    }

    private void startListeningForAlerts() {
        sosAlertsRef = FirebaseDatabase.getInstance().getReference("sos_alerts").child(currentUid);
        initialLoadDone = false;

        sosListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String prevKey) {
                String pushId = snapshot.getKey();
                if (pushId == null) return;

                // If this is an old alert from an hour ago, just add to 'seen' and ignore
                if (!initialLoadDone) {
                    synchronized (seenIds) { seenIds.add(pushId); }
                    return;
                }
                handleSnapshot(snapshot);
            }
            @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
            @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };

        sosAlertsRef.addChildEventListener(sosListener);

        // Mark the initial load as finished after the first full fetch
        sosAlertsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                initialLoadDone = true;
                Log.d(TAG, "Initial SOS sync complete. Ready for live alerts.");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { initialLoadDone = true; }
        });
    }

    private void handleSnapshot(@NonNull DataSnapshot snapshot) {
        String pushId = snapshot.getKey();
        synchronized (seenIds) {
            if (seenIds.contains(pushId)) return;
            seenIds.add(pushId);
            if (seenIds.size() > MAX_SEEN) {
                Iterator<String> it = seenIds.iterator();
                if (it.hasNext()) { it.next(); it.remove(); }
            }
        }

        String name = snapshot.child("senderName").getValue(String.class);
        String fromUid = snapshot.child("fromUid").getValue(String.class);
        Double lat = snapshot.child("lat").getValue(Double.class);
        Double lng = snapshot.child("lng").getValue(Double.class);

        sendEmergencyNotification(name != null ? name : "A Friend", fromUid, lat, lng, pushId.hashCode());
        triggerEmergencyHaptics();
    }

    private void sendEmergencyNotification(String name, String uid, Double lat, Double lng, int notifId) {
        // ACTION 1: Open the list of alerts
        Intent listIntent = new Intent(this, SosAlertsActivity.class);
        PendingIntent piList = PendingIntent.getActivity(this, notifId, listIntent, PendingIntent.FLAG_IMMUTABLE);

        // ACTION 2: Open Map directly (if coordinates exist)
        Intent mapIntent = new Intent(this, MapActivity.class);
        if (lat != null && lng != null) {
            mapIntent.putExtra("open_lat", lat);
            mapIntent.putExtra("open_lng", lng);
            mapIntent.putExtra("friendUid", uid);
        }
        PendingIntent piMap = PendingIntent.getActivity(this, notifId + 1, mapIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sos)
                .setContentTitle("SOS: " + name + " needs help!")
                .setContentText("Emergency signal received. Tap to view location.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVibrate(new long[]{0, 1000, 500, 1000})
                .setAutoCancel(true)
                .setContentIntent(piList)
                .addAction(R.drawable.ic_map, "VIEW ON MAP", piMap); // Pro Feature: Direct Map Access

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(notifId, builder.build());
    }

    private void triggerEmergencyHaptics() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 200, 500}, -1));
            } else {
                v.vibrate(1000);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Emergency Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Critical notifications for friends in danger");
            channel.enableVibration(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Keeps the guardian alive if the system kills it
    }

    @Override public void onDestroy() {
        super.onDestroy();
        if (sosListener != null) sosAlertsRef.removeEventListener(sosListener);
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}