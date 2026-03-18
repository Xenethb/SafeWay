package com.s23010602.safeway;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.*;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.HashMap;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final int REQUEST_VIDEO_PERMISSIONS = 101;
    private final String[] VIDEO_PERMISSIONS = { Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO };
    private static final long SOS_DEDUP_WINDOW_MS = 5 * 60 * 1000L;
    private static final String PREFS_NAME = "SafeWayPrefs";
    private static final String ACTIVATION_PREFS_NAME = "activation_settings";

    private FirebaseAuth mAuth;
    private Switch monitorSwitch;
    private Button sosButton;
    private Handler alertHandler;
    private Runnable alertRunnable;
    private SharedPreferences sharedPreferences;
    private static final String KEY_PERMISSIONS_GRANTED = "permissions_granted";
    private boolean sosIntentHandled = false;
    private MediaPlayer beepPlayer; // Reuse player to save memory

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!sharedPreferences.getBoolean(KEY_PERMISSIONS_GRANTED, false)) {
            requestRequiredPermissions();
        }

        setupUI();
        handleIntent(getIntent());
    }

    private void setupUI() {
        sosButton = findViewById(R.id.btnSOS);
        monitorSwitch = findViewById(R.id.switchMonitor);
        ImageButton btnSosAlerts = findViewById(R.id.btnSosAlerts);

        // Feature Navigation
        findViewById(R.id.btnSearch).setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        findViewById(R.id.btnManage).setOnClickListener(v -> startActivity(new Intent(this, ManageActivationActivity.class)));
        findViewById(R.id.imgProfile).setOnClickListener(v -> startActivity(new Intent(this, MyProfileActivity.class)));

        if (btnSosAlerts != null) {
            btnSosAlerts.setOnClickListener(v -> startActivity(new Intent(this, SosAlertsActivity.class)));
        }

        sosButton.setOnClickListener(v -> {
            if (!hasVideoPermissions()) {
                ActivityCompat.requestPermissions(this, VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
            } else {
                showSosCountdownDialog();
            }
        });

        // Bottom Nav
        findViewById(R.id.btnMap).setOnClickListener(v -> startActivity(new Intent(this, MapActivity.class)));
        findViewById(R.id.btnSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnFriends).setOnClickListener(v -> startActivity(new Intent(this, FriendsActivity.class)));
        findViewById(R.id.btnHistory).setOnClickListener(v -> startActivity(new Intent(this, AlertHistoryActivity.class)));

        monitorSwitch.setOnCheckedChangeListener((bv, isChecked) -> {
            if (isChecked) startMonitoringService();
            else stopMonitoringService();
        });
    }

    private void handleIntent(Intent it) {
        if (it == null) return;
        if (it.getBooleanExtra("trigger_sos", false)) {
            handleIncomingSosIntent(it);
        } else {
            String showUid = it.getStringExtra("show_map_for_uid");
            if (showUid != null) {
                Intent mapIntent = new Intent(this, MapActivity.class);
                mapIntent.putExtra("friendUid", showUid);
                startActivity(mapIntent);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIncomingSosIntent(Intent it) {
        if (sosIntentHandled) return;
        sosIntentHandled = true;

        if (wasRecentSosSent()) {
            Toast.makeText(this, "Emergency already active — opening camera", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, CameraActivity.class));
            return;
        }
        showSosCountdownDialog();
    }

    private boolean wasRecentSosSent() {
        long last = getSharedPreferences(ACTIVATION_PREFS_NAME, MODE_PRIVATE).getLong("last_sos_time_ms", 0L);
        return (System.currentTimeMillis() - last) < SOS_DEDUP_WINDOW_MS;
    }

    private void showSosCountdownDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_sos_countdown);
        dialog.setCancelable(false);

        Window window = dialog.getWindow();
        if (window != null) window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

        TextView tvCountdown = dialog.findViewById(R.id.tvCountdown);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);

        new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millis) {
                tvCountdown.setText(String.valueOf(millis / 1000));
            }
            @Override
            public void onFinish() {
                stopAlerts();
                dialog.dismiss();
                sendSOSAlert();
            }
        }.start();

        btnCancel.setOnClickListener(v -> {
            stopAlerts();
            dialog.dismiss();
        });

        dialog.show();
        startRepeatingAlert();
    }

    private void startRepeatingAlert() {
        alertHandler = new Handler(Looper.getMainLooper());
        alertRunnable = new Runnable() {
            @Override
            public void run() {
                playBeep();
                vibratePhone();
                alertHandler.postDelayed(this, 2000);
            }
        };
        alertHandler.post(alertRunnable);
    }

    private void stopAlerts() {
        if (alertHandler != null) alertHandler.removeCallbacks(alertRunnable);
        if (beepPlayer != null) {
            beepPlayer.stop();
            beepPlayer.release();
            beepPlayer = null;
        }
    }

    private void playBeep() {
        if (beepPlayer == null) beepPlayer = MediaPlayer.create(this, R.raw.siren);
        beepPlayer.start();
    }

    private void vibratePhone() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(400);
            }
        }
    }

    private void sendSOSAlert() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        // 1. Mark timestamp immediately
        getSharedPreferences(ACTIVATION_PREFS_NAME, MODE_PRIVATE).edit()
                .putLong("last_sos_time_ms", System.currentTimeMillis()).apply();

        // 2. Immediate feedback
        Toast.makeText(this, "SOS Sent. Recording evidence...", Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, CameraActivity.class));

        // 3. Location & Data Write
        FusedLocationProviderClient fl = LocationServices.getFusedLocationProviderClient(this);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fl.getLastLocation().addOnSuccessListener(loc -> preparePayload(uid, loc));
        } else {
            preparePayload(uid, null);
        }
    }

    private void preparePayload(String uid, android.location.Location loc) {
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                String name = snap.child("displayName").getValue(String.class);
                if (name == null) name = snap.child("username").getValue(String.class);
                if (name == null) name = "A Friend";

                Map<String, Object> sosData = new HashMap<>();
                sosData.put("fromUid", uid);
                sosData.put("senderName", name);
                sosData.put("timestamp", System.currentTimeMillis());
                if (loc != null) {
                    sosData.put("lat", loc.getLatitude());
                    sosData.put("lng", loc.getLongitude());
                }

                performAtomicSosUpdate(uid, sosData);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    private void performAtomicSosUpdate(String myUid, Map<String, Object> data) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        root.child("friends").child(myUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                Map<String, Object> updates = new HashMap<>();

                // Add to every friend's alert node
                for (DataSnapshot friend : snap.getChildren()) {
                    String fUid = friend.getKey();
                    if (fUid != null) {
                        String alertId = root.child("sos_alerts").child(fUid).push().getKey();
                        updates.put("/sos_alerts/" + fUid + "/" + alertId, data);
                    }
                }

                // Add to my own history
                String histId = root.child("users").child(myUid).child("sos_history").push().getKey();
                updates.put("/users/" + myUid + "/sos_history/" + histId, data);

                // ATOMIC WRITE: All or nothing
                root.updateChildren(updates);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    private void startMonitoringService() {
        Intent si = new Intent(this, SosMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(si);
        else startService(si);
    }

    private void stopMonitoringService() {
        stopService(new Intent(this, SosMonitorService.class));
    }

    private boolean hasVideoPermissions() {
        for (String p : VIDEO_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void requestRequiredPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
        }, PERMISSION_REQUEST_CODE);
    }
}