package com.s23010602.safeway;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

/**
 * PRODUCTION READY: Manages the local evidence vault.
 * Handles batch deletion of videos and metadata.
 */
public class AlertHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerAlerts;
    private AlertLogAdapter adapter;
    private Button btnDelete;
    private TextView tvEmptyHistory; // Added for better UX

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert_history);

        // UI Initialization
        recyclerAlerts = findViewById(R.id.recyclerAlerts);
        btnDelete = findViewById(R.id.btnDelete);
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory);

        recyclerAlerts.setLayoutManager(new LinearLayoutManager(this));

        loadHistory();
    }

    private void loadHistory() {
        List<AlertLog> logs = AlertLogger.getAllAlerts(this);

        if (logs.isEmpty()) {
            showEmptyState(true);
        } else {
            showEmptyState(false);
            adapter = new AlertLogAdapter(this, logs);
            recyclerAlerts.setAdapter(adapter);

            adapter.setOnSelectionChangedListener(selectedCount -> {
                btnDelete.setVisibility(selectedCount > 0 ? View.VISIBLE : View.GONE);
                // Using string resources for "Delete (x)" is recommended for production
                btnDelete.setText(getString(R.string.history_delete_count, selectedCount));
            });
        }

        btnDelete.setOnClickListener(v -> deleteSelectedLogs());
    }

    private void showEmptyState(boolean isEmpty) {
        if (tvEmptyHistory != null) {
            tvEmptyHistory.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        recyclerAlerts.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void deleteSelectedLogs() {
        List<AlertLog> selectedLogs = adapter.getSelectedLogs();
        int deletedCount = 0;

        for (AlertLog log : selectedLogs) {
            // 1. Delete the physical video file to free up space
            if (log.getVideoPath() != null) {
                File videoFile = new File(log.getVideoPath());
                if (videoFile.exists()) {
                    videoFile.delete();
                }
            }

            // 2. Remove from SharedPreferences
            AlertLogger.deleteAlert(this, log);
            deletedCount++;
        }

        // 3. Update the list in the UI
        adapter.removeLogs(selectedLogs);

        // Check if list is now empty after deletion
        if (adapter.getItemCount() == 0) {
            showEmptyState(true);
        }

        btnDelete.setVisibility(View.GONE);
        Toast.makeText(this, "Deleted " + deletedCount + " records", Toast.LENGTH_SHORT).show();
    }
}