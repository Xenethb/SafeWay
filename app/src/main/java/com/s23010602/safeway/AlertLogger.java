package com.s23010602.safeway;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * PRODUCTION READY: Manages local storage of alert metadata.
 * Uses synchronized blocks to ensure data integrity during background saves.
 */
public class AlertLogger {

    private static final String PREF_NAME = "AlertLogsPref";
    private static final String KEY_ALERT_LOGS = "alert_logs";

    // Efficient: One instance reused for all operations
    private static final Gson gson = new GsonBuilder().create();

    /**
     * Saves an alert to the top of the list.
     * Synchronized to prevent conflicts between UI and Background Services.
     */
    public static synchronized void saveAlert(Context context, AlertLog log) {
        if (log == null) return;

        List<AlertLog> logs = getAllAlerts(context);
        logs.add(0, log);
        saveAllAlerts(context, logs);
    }

    public static synchronized List<AlertLog> getAllAlerts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_ALERT_LOGS, null);

        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            Type type = new TypeToken<List<AlertLog>>() {}.getType();
            List<AlertLog> decoded = gson.fromJson(json, type);
            return (decoded != null) ? decoded : new ArrayList<>();
        } catch (Exception e) {
            // If data is corrupted, return empty list instead of crashing
            return new ArrayList<>();
        }
    }

    public static synchronized void saveAllAlerts(Context context, List<AlertLog> logs) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        String json = gson.toJson(logs);
        editor.putString(KEY_ALERT_LOGS, json);
        editor.apply();
    }

    public static synchronized void deleteAlert(Context context, AlertLog logToDelete) {
        if (logToDelete == null || logToDelete.getVideoPath() == null) return;

        List<AlertLog> logs = getAllAlerts(context);
        // Uses modern Java 8+ removeIf for efficiency
        logs.removeIf(log -> log.getVideoPath().equals(logToDelete.getVideoPath()));
        saveAllAlerts(context, logs);
    }

    /**
     * Essential for a real app: Allow the user to clear their history.
     */
    public static void clearAllLogs(Context context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ALERT_LOGS)
                .apply();
    }
}