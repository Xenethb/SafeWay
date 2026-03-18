package com.s23010602.safeway;

import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.*;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.*;

/**
 * PRODUCTION READY: Background service for routine safety tracking.
 * Optimized for low battery impact and Android 14+ compatibility.
 */
public class LiveLocationService extends Service {

    private static final String TAG = "LiveLocationService";
    private static final String CHANNEL_ID = "LiveLocationChannel";
    public static final String ACTION_STOP_LOCATION_SERVICE = "STOP_LOCATION_SERVICE";

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private DatabaseReference locationRef;

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Check if user is logged in before doing anything
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            stopSelf();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        locationRef = FirebaseDatabase.getInstance().getReference("location_sharing").child(uid);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        createNotificationChannel();
        startMyForeground();
        requestLocationUpdates();
    }

    private void requestLocationUpdates() {
        // BALANCED accuracy is perfect for 5-minute intervals.
        // HIGH_ACCURACY would drain the battery 3x faster for this use case.
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                5 * 60 * 1000 // 5 Minutes
        )
                .setMinUpdateIntervalMillis(2 * 60 * 1000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location != null) {
                    updateFirebase(location);
                }
            }
        };

        // Fail-safe: If permission is lost while service is running, stop the service.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permissions missing, stopping service.");
            stopSelf();
            return;
        }

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    private void updateFirebase(Location location) {
        // PRO TIP: Use updateChildren instead of setValue.
        // This avoids overwriting other fields (like 'name') if they were set elsewhere.
        Map<String, Object> updates = new HashMap<>();
        updates.put("lat", location.getLatitude());
        updates.put("lng", location.getLongitude());
        updates.put("timestamp", ServerValue.TIMESTAMP); // Use Firebase Server Time for accuracy

        locationRef.updateChildren(updates).addOnFailureListener(e ->
                Log.e(TAG, "Firebase Update Failed: " + e.getMessage()));
    }

    private void startMyForeground() {
        Intent notificationIntent = new Intent(this, HomeActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, LiveLocationService.class);
        stopIntent.setAction(ACTION_STOP_LOCATION_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0,
                stopIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Safeway Active")
                .setContentText("Your location is being shared with protectors.")
                .setSmallIcon(R.drawable.ic_location_on)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Sharing", stopPendingIntent)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(1, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Live Location Monitoring",
                    NotificationManager.IMPORTANCE_LOW // Keeps the notification quiet
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_LOCATION_SERVICE.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        // START_STICKY ensures the system restarts the service if it is killed for memory.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}