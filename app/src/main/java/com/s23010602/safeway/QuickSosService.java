package com.s23010602.safeway;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * PRODUCTION READY: Background monitor for emergency triggers.
 * Handles shake detection and volume-key confirmation for SOS.
 */
public class QuickSosService extends Service implements SensorEventListener {
    private static final String TAG = "QuickSosService";
    public static final String CHANNEL_ID = "quick_sos_channel";
    public static final int NOTIF_ID = 1001;
    public static final String ACTION_CANCEL = "com.s23010602.safeway.ACTION_CANCEL_SOS";

    private SensorManager sensorManager;
    private Sensor accel;
    private boolean shakeDetected = false;
    private boolean volumeReceiverRegistered = false;
    private BroadcastReceiver volumeReceiver;
    private volatile boolean sendingInProgress = false;

    private final float[] gravity = new float[3];
    private static final float ALPHA = 0.8f;
    private static final float LINEAR_ACCEL_THRESHOLD = 14.0f; // Slightly higher to avoid false positives
    private long lastShakeTime = 0L;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        // The "Volume Hack": Listening for volume changes as a secondary confirmation
        volumeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (shakeDetected && !sendingInProgress) {
                    shakeDetected = false;
                    sendSosToFirebase();
                }
            }
        };
        registerTriggerMethod();
    }

    private void registerTriggerMethod() {
        SharedPreferences prefs = getSharedPreferences("activation_settings", MODE_PRIVATE);
        int methodId = prefs.getInt("trigger_method", R.id.radio_shake);

        // Standard shake detection
        if ((methodId == R.id.radio_shake || methodId == R.id.radio_shake_volume) && accel != null) {
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL);
        }

        // Enhanced: Shake + Volume Press
        if (methodId == R.id.radio_shake_volume && !volumeReceiverRegistered) {
            IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(volumeReceiver, filter);
            }
            volumeReceiverRegistered = true;
        }
    }

    private void sendSosToFirebase() {
        if (sendingInProgress) return;
        sendingInProgress = true;

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() == null) {
            sendingInProgress = false;
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();

        // Ensure LiveLocation starts immediately to give friends a trail
        Intent liveIntent = new Intent(this, LiveLocationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(liveIntent);
        else startService(liveIntent);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            FusedLocationProviderClient flClient = LocationServices.getFusedLocationProviderClient(this);
            flClient.getLastLocation().addOnCompleteListener(task -> {
                Map<String, Object> payload = new HashMap<>();
                payload.put("timestamp", System.currentTimeMillis());
                payload.put("uid", uid);
                payload.put("status", "EMERGENCY_SHAKE");

                if (task.isSuccessful() && task.getResult() != null) {
                    payload.put("lat", task.getResult().getLatitude());
                    payload.put("lng", task.getResult().getLongitude());
                }
                writeSosToFriends(uid, payload);
            });
        }
    }

    private void writeSosToFriends(String myUid, Map<String, Object> payload) {
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child("users").child(myUid).child("displayName").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                String name = snap.getValue(String.class);
                payload.put("senderName", name != null ? name : "User Alert");

                db.child("friends").child(myUid).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot friendSnap) {
                        Map<String, Object> updates = new HashMap<>();
                        for (DataSnapshot friend : friendSnap.getChildren()) {
                            String fId = friend.getKey();
                            if (fId != null) {
                                String alertId = db.child("sos_alerts").child(fId).push().getKey();
                                updates.put("/sos_alerts/" + fId + "/" + alertId, payload);
                            }
                        }
                        // Also write to user's own history
                        String histId = db.child("users").child(myUid).child("sos_history").push().getKey();
                        updates.put("/users/" + myUid + "/sos_history/" + histId, payload);

                        db.updateChildren(updates).addOnCompleteListener(t -> {
                            sendingInProgress = false;
                            updateNotification(true);
                            Log.d(TAG, "SOS broadcast successful");
                        });
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) { sendingInProgress = false; }
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { sendingInProgress = false; }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // High-pass filter to remove gravity
        gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0];
        gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1];
        gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2];

        float lx = event.values[0] - gravity[0];
        float ly = event.values[1] - gravity[1];
        float lz = event.values[2] - gravity[2];

        double linearMag = Math.sqrt(lx*lx + ly*ly + lz*lz);

        if (linearMag > LINEAR_ACCEL_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > 3000L && !sendingInProgress) {
                lastShakeTime = now;

                int method = getSharedPreferences("activation_settings", MODE_PRIVATE)
                        .getInt("trigger_method", R.id.radio_shake);

                if (method == R.id.radio_shake) {
                    sendSosToFirebase();
                } else if (method == R.id.radio_shake_volume) {
                    shakeDetected = true; // Wait for volume press
                    Toast.makeText(this, "Shake detected! Press Volume to send SOS.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        startServiceInForeground();
        return START_STICKY;
    }

    private void startServiceInForeground() {
        Notification notification = createNotification(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // UPDATED: Removed MICROPHONE type as it is not used in this service scope
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC | ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void updateNotification(boolean sent) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, createNotification(sent));
    }

    private Notification createNotification(boolean sent) {
        Intent cancelIntent = new Intent(this, QuickSosService.class).setAction(ACTION_CANCEL);
        PendingIntent cancelPi = PendingIntent.getService(this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sos)
                .setContentTitle(sent ? "SOS SENT" : "SafeWay Protect: ON")
                .setContentText(sent ? "Help is on the way." : "Monitoring for emergency triggers...")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", cancelPi)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Quick SOS Monitoring", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Override public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (volumeReceiverRegistered) unregisterReceiver(volumeReceiver);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override public IBinder onBind(Intent intent) { return null; }
}