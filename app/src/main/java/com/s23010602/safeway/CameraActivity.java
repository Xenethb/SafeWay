package com.s23010602.safeway;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.common.api.ResolvableApiException;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class CameraActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 101;
    private static final int REQUEST_LOCATION_SETTINGS = 102;
    private static final int RECORD_DURATION_MS = 30_000;
    private static final String TAG = "CameraActivity";

    private PreviewView previewView;
    private VideoRecorder videoRecorder;
    private String outputPath;
    private Handler handler;
    private Runnable stopRunnable;

    private FusedLocationProviderClient fusedLocationClient;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler(Looper.getMainLooper());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Root layout setup
        FrameLayout rootLayout = new FrameLayout(this);
        previewView = new PreviewView(this);
        previewView.setId(View.generateViewId());
        rootLayout.addView(previewView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        setContentView(rootLayout);

        addOverlayButtons(rootLayout);

        if (!hasPermissions()) {
            requestPermissions();
        } else {
            checkLocationEnabled();
            startRecording();
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                cancelAndDeleteVideo();
            }
        });
    }

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, REQUEST_PERMISSIONS);
    }

    private void addOverlayButtons(FrameLayout rootLayout) {
        Button cancelButton = new Button(this);
        cancelButton.setText(getString(R.string.cam_cancel));
        cancelButton.setBackgroundColor(0xAAFF0000);
        cancelButton.setTextColor(0xFFFFFFFF);
        cancelButton.setOnClickListener(v -> cancelAndDeleteVideo());

        Button stopSaveButton = new Button(this);
        stopSaveButton.setText(getString(R.string.cam_stop_save));
        stopSaveButton.setBackgroundColor(0xAA00CC00);
        stopSaveButton.setTextColor(0xFFFFFFFF);
        stopSaveButton.setOnClickListener(v -> stopRecordingAndLog(true));

        FrameLayout.LayoutParams cancelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelParams.gravity = Gravity.TOP | Gravity.END;
        cancelParams.setMargins(30, 60, 30, 30);

        FrameLayout.LayoutParams saveParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveParams.gravity = Gravity.TOP | Gravity.START;
        saveParams.setMargins(30, 60, 30, 30);

        rootLayout.addView(cancelButton, cancelParams);
        rootLayout.addView(stopSaveButton, saveParams);
    }

    private void startRecording() {
        File movieDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (movieDir != null && !movieDir.exists()) movieDir.mkdirs();

        outputPath = movieDir + "/sos_" + UUID.randomUUID().toString() + ".mp4";
        videoRecorder = new VideoRecorder();

        videoRecorder.startRecording(this, previewView, outputPath, () -> {
            isRecording = true;
            logLocation();

            stopRunnable = () -> stopRecordingAndLog(true);
            handler.postDelayed(stopRunnable, RECORD_DURATION_MS);
            return null;
        });
    }

    private void cancelAndDeleteVideo() {
        stopRecordingCleanup();
        File file = new File(outputPath);
        if (file.exists()) {
            boolean deleted = file.delete();
            Log.d(TAG, "File deleted: " + deleted);
        }
        Toast.makeText(this, getString(R.string.cam_canceled), Toast.LENGTH_SHORT).show();
        goToHome();
    }

    private void stopRecordingAndLog(boolean shouldLog) {
        if (!isRecording) {
            goToHome();
            return;
        }

        stopRecordingCleanup();

        if (shouldLog) {
            saveMetadataAndFinish();
        } else {
            goToHome();
        }
    }

    private void stopRecordingCleanup() {
        if (handler != null && stopRunnable != null) {
            handler.removeCallbacks(stopRunnable);
        }
        if (videoRecorder != null) {
            videoRecorder.stopRecording();
        }
        isRecording = false;
    }

    private void saveMetadataAndFinish() {
        long now = System.currentTimeMillis();
        String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(now));

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(loc -> {
                double lat = (loc != null) ? loc.getLatitude() : 0.0;
                double lng = (loc != null) ? loc.getLongitude() : 0.0;

                // UPDATED: Using the new constructor with timestamp and coordinates
                AlertLog log = new AlertLog(now, dateTime, lat, lng, outputPath);

                AlertLogger.saveAlert(this, log);
                Toast.makeText(this, getString(R.string.cam_saved), Toast.LENGTH_SHORT).show();
                goToHome();
            });
        } else {
            // Fallback if no location permission
            AlertLog log = new AlertLog(now, dateTime, 0.0, 0.0, outputPath);
            AlertLogger.saveAlert(this, log);
            goToHome();
        }
    }

    private void goToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @SuppressLint("MissingPermission")
    private void logLocation() {
        if (hasPermissions()) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    Log.d(TAG, "Recording started at: " + location.getLatitude() + ", " + location.getLongitude());
                }
            });
        }
    }

    private void checkLocationEnabled() {
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build();
        LocationSettingsRequest settingsRequest = new LocationSettingsRequest.Builder()
                .addLocationRequest(request)
                .setAlwaysShow(true)
                .build();

        SettingsClient client = LocationServices.getSettingsClient(this);
        client.checkLocationSettings(settingsRequest)
                .addOnFailureListener(e -> {
                    if (e instanceof ResolvableApiException) {
                        try {
                            ((ResolvableApiException) e).startResolutionForResult(this, REQUEST_LOCATION_SETTINGS);
                        } catch (IntentSender.SendIntentException ex) {
                            Log.e(TAG, "Location settings resolution failed", ex);
                        }
                    }
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        // CRITICAL: Stop recording if the user leaves the app (e.g., incoming call)
        if (isRecording) {
            stopRecordingAndLog(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS && hasPermissions()) {
            checkLocationEnabled();
            startRecording();
        } else {
            Toast.makeText(this, getString(R.string.cam_perm_required), Toast.LENGTH_LONG).show();
            finish();
        }
    }
}