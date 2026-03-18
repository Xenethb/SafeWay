package com.s23010602.safeway;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class ManageActivationActivity extends AppCompatActivity {

    private static final String PREF_NAME = "activation_settings";
    private static final String[] dayCodes = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};
    private static final int REQ_LOCATION_ENABLE = 1001;
    private static final int REQ_LOCATION_PERMISSION = 2001;

    private final Set<String> selectedDays = new HashSet<>();
    private SharedPreferences preferences;
    private FirebaseUser currentUser;

    private SwitchCompat timeSwitch;
    private Spinner fromHour, fromMin, fromAmPm, durationSpinner;
    private RadioGroup triggerGroup;
    private Button enableLocationBtn;

    private boolean isLiveLocationOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_activation);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            finish();
            return;
        }

        preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // UI Binding
        enableLocationBtn = findViewById(R.id.btn_enable_location);
        timeSwitch = findViewById(R.id.switch_enable_time);
        fromHour = findViewById(R.id.spinner_from_hour);
        fromMin = findViewById(R.id.spinner_from_min);
        fromAmPm = findViewById(R.id.spinner_from_ampm);
        durationSpinner = findViewById(R.id.spinner_duration_hour);
        triggerGroup = findViewById(R.id.trigger_method_group);

        setupSpinners();
        setupDayButtons();
        loadSettings();

        // Listeners
        AdapterView.OnItemSelectedListener selListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { saveSettings(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        };
        fromHour.setOnItemSelectedListener(selListener);
        fromMin.setOnItemSelectedListener(selListener);
        fromAmPm.setOnItemSelectedListener(selListener);
        durationSpinner.setOnItemSelectedListener(selListener);

        triggerGroup.setOnCheckedChangeListener((group, id) -> saveSettings());

        timeSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            saveSettings();
            if (isChecked) {
                checkAlarmPermissions();
                scheduleWeeklyAlarms();
            } else {
                cancelAlarms();
            }
        });

        enableLocationBtn.setOnClickListener(v -> {
            if (!isLocationEnabled()) requestEnableLocation();
            else toggleLiveLocation();
        });
    }

    private void setupSpinners() {
        setupSpinner(fromHour, getNumberArray(1, 12));
        setupSpinner(fromMin, getNumberArray(0, 59));
        setupSpinner(fromAmPm, new String[]{"AM", "PM"});
        setupSpinner(durationSpinner, getResources().getStringArray(R.array.hours_array));
    }

    private void setupDayButtons() {
        for (String code : dayCodes) {
            int resId = getResources().getIdentifier("btn_" + code, "id", getPackageName());
            Button dayBtn = findViewById(resId);
            if (dayBtn != null) {
                dayBtn.setOnClickListener(v -> {
                    if (selectedDays.contains(code)) selectedDays.remove(code);
                    else selectedDays.add(code);
                    updateDayButtons();
                    saveSettings();
                });
            }
        }
    }

    private void checkAlarmPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
            }
        }
    }

    private void requestEnableLocation() {
        // MODERN API: LocationRequest.Builder
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build();

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        SettingsClient client = LocationServices.getSettingsClient(this);

        client.checkLocationSettings(builder.build())
                .addOnSuccessListener(resp -> toggleLiveLocation())
                .addOnFailureListener(e -> {
                    if (e instanceof ResolvableApiException) {
                        try {
                            ((ResolvableApiException) e).startResolutionForResult(this, REQ_LOCATION_ENABLE);
                        } catch (IntentSender.SendIntentException ex) {
                            ex.printStackTrace();
                        }
                    }
                });
    }

    private void toggleLiveLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION_PERMISSION);
            return;
        }

        isLiveLocationOn = !isLiveLocationOn;
        Intent liveIntent = new Intent(this, LiveLocationService.class);
        if (isLiveLocationOn) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(liveIntent);
            else startService(liveIntent);
        } else {
            stopService(liveIntent);
        }
        updateLocationButton();
        saveSettings();
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("time_switch", timeSwitch.isChecked());
        editor.putBoolean("live_location", isLiveLocationOn);
        editor.putStringSet("days", new HashSet<>(selectedDays));
        editor.putInt("from_hour", fromHour.getSelectedItemPosition());
        editor.putInt("from_min", fromMin.getSelectedItemPosition());
        editor.putInt("from_ampm", fromAmPm.getSelectedItemPosition());
        editor.putInt("duration", durationSpinner.getSelectedItemPosition());
        editor.putInt("trigger_method", triggerGroup.getCheckedRadioButtonId());
        editor.apply();

        // Firebase Sync
        FirebaseDatabase.getInstance().getReference("users")
                .child(currentUser.getUid())
                .child("activation_settings")
                .setValue(new ActivationData(
                        new ArrayList<>(selectedDays),
                        timeSwitch.isChecked(),
                        fromHour.getSelectedItemPosition(),
                        fromMin.getSelectedItemPosition(),
                        fromAmPm.getSelectedItemPosition(),
                        durationSpinner.getSelectedItemPosition(),
                        triggerGroup.getCheckedRadioButtonId(),
                        isLiveLocationOn
                ));

        // Reschedule if enabled
        if (timeSwitch.isChecked()) scheduleWeeklyAlarms();
    }

    private void scheduleWeeklyAlarms() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        int spinnerHour = fromHour.getSelectedItemPosition() + 1; // 1-12
        int spinnerMin = fromMin.getSelectedItemPosition();
        int spinnerAmPm = fromAmPm.getSelectedItemPosition(); // 0=AM, 1=PM
        int hour24 = (spinnerHour % 12) + (spinnerAmPm * 12);

        // Day mapping
        int[] calendarDays = {Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY};

        for (int i = 0; i < dayCodes.length; i++) {
            Intent actIntent = new Intent(this, ActivationReceiver.class);
            // PRO TIP: Unique request codes (i) ensure we don't overwrite alarms
            PendingIntent pi = PendingIntent.getBroadcast(this, i, actIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            if (selectedDays.contains(dayCodes[i])) {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.DAY_OF_WEEK, calendarDays[i]);
                cal.set(Calendar.HOUR_OF_DAY, hour24);
                cal.set(Calendar.MINUTE, spinnerMin);
                cal.set(Calendar.SECOND, 0);

                if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                    cal.add(Calendar.WEEK_OF_YEAR, 1);
                }

                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(cal.getTimeInMillis(), pi);
                am.setAlarmClock(info, pi);
            } else {
                am.cancel(pi);
            }
        }
    }

    private void cancelAlarms() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        for (int i = 0; i < dayCodes.length; i++) {
            Intent intent = new Intent(this, ActivationReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(this, i, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (am != null) am.cancel(pi);
        }
        stopService(new Intent(this, LiveLocationService.class));
    }

    private void loadSettings() {
        timeSwitch.setChecked(preferences.getBoolean("time_switch", false));
        isLiveLocationOn = preferences.getBoolean("live_location", false);
        selectedDays.clear();
        selectedDays.addAll(preferences.getStringSet("days", new HashSet<>()));

        fromHour.setSelection(preferences.getInt("from_hour", 0));
        fromMin.setSelection(preferences.getInt("from_min", 0));
        fromAmPm.setSelection(preferences.getInt("from_ampm", 0));
        durationSpinner.setSelection(preferences.getInt("duration", 0));

        int triggerId = preferences.getInt("trigger_method", R.id.radio_shake);
        if (findViewById(triggerId) != null) triggerGroup.check(triggerId);

        updateDayButtons();
        updateLocationButton();
    }

    // Helper Methods
    private void setupSpinner(Spinner s, String[] items) {
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(ad);
    }

    private String[] getNumberArray(int start, int end) {
        String[] arr = new String[end - start + 1];
        for (int i = start; i <= end; i++) arr[i - start] = String.format("%02d", i);
        return arr;
    }

    private boolean isLocationEnabled() {
        LocationManager mgr = (LocationManager) getSystemService(LOCATION_SERVICE);
        return mgr != null && mgr.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private void updateDayButtons() {
        for (String code : dayCodes) {
            int id = getResources().getIdentifier("btn_" + code, "id", getPackageName());
            Button b = findViewById(id);
            if (b == null) continue;
            boolean selected = selectedDays.contains(code);
            b.setBackgroundResource(selected ? R.drawable.circle_teal : R.drawable.circle_gray);
            b.setTextColor(getColor(selected ? R.color.white : R.color.black));
        }
    }

    private void updateLocationButton() {
        enableLocationBtn.setText(isLiveLocationOn ? "Disable Live Location" : "Enable Live Location Now");
        enableLocationBtn.setBackgroundColor(getColor(isLiveLocationOn ? R.color.red : R.color.green));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_LOCATION_ENABLE && resultCode == RESULT_OK) toggleLiveLocation();
    }

    public static class ActivationData {
        public ArrayList<String> days;
        public boolean timeBased;
        public int fromHour, fromMin, fromAmPm, duration, triggerMethod;
        public boolean liveLocation;

        public ActivationData() {}
        public ActivationData(ArrayList<String> days, boolean timeBased, int fromHour, int fromMin,
                              int fromAmPm, int duration, int triggerMethod, boolean liveLocation) {
            this.days = days; this.timeBased = timeBased; this.fromHour = fromHour;
            this.fromMin = fromMin; this.fromAmPm = fromAmPm; this.duration = duration;
            this.triggerMethod = triggerMethod; this.liveLocation = liveLocation;
        }
    }
}