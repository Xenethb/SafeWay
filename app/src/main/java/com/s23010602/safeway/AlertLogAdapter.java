package com.s23010602.safeway;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider; // Recommended for production
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AlertLogAdapter extends RecyclerView.Adapter<AlertLogAdapter.AlertLogViewHolder> {

    private Context context;
    private List<AlertLog> alertLogs;
    private List<Integer> selectedPositions = new ArrayList<>();
    private OnSelectionChangedListener selectionChangedListener;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    public AlertLogAdapter(Context context, List<AlertLog> alertLogs) {
        this.context = context;
        this.alertLogs = alertLogs;
    }

    @NonNull
    @Override
    public AlertLogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_alert_log, parent, false);
        return new AlertLogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlertLogViewHolder holder, int position) {
        AlertLog log = alertLogs.get(position);

        // FIXED: Use the new 'getFormattedDate' method
        holder.txtDateTime.setText(log.getFormattedDate());

        // FIXED: Build a location string from Latitude and Longitude
        if (log.getLatitude() != 0 || log.getLongitude() != 0) {
            String locStr = String.format(Locale.getDefault(), "Lat: %.4f, Lng: %.4f",
                    log.getLatitude(), log.getLongitude());
            holder.txtLocation.setText(locStr);
        } else {
            holder.txtLocation.setText("No location data");
        }

        // Highlight selection
        holder.itemView.setBackgroundColor(selectedPositions.contains(position) ? 0x5533B5E5 : 0xFFEEEEEE);

        // Click listeners
        holder.btnPlay.setOnClickListener(v -> playVideo(log));

        holder.itemView.setOnLongClickListener(v -> {
            toggleSelection(position);
            return true;
        });

        holder.itemView.setOnClickListener(v -> {
            if (!selectedPositions.isEmpty()) {
                toggleSelection(position);
            } else {
                playVideo(log);
            }
        });
    }

    /**
     * PRO TIP: Use FileProvider to open videos.
     * Direct file:// URIs often cause crashes on modern Android (7.0+).
     */
    private void playVideo(AlertLog log) {
        File file = new File(log.getVideoPath());
        if (!file.exists()) {
            android.widget.Toast.makeText(context, "Video file missing", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Get URI using FileProvider defined in your Manifest
            Uri videoUri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".provider", file);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(videoUri, "video/mp4");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (Exception e) {
            android.util.Log.e("AlertLogAdapter", "Error playing video", e);
        }
    }

    private void toggleSelection(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(Integer.valueOf(position));
        } else {
            selectedPositions.add(position);
        }
        notifyItemChanged(position);

        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(selectedPositions.size());
        }
    }

    public List<AlertLog> getSelectedLogs() {
        List<AlertLog> selectedLogs = new ArrayList<>();
        for (int pos : selectedPositions) {
            selectedLogs.add(alertLogs.get(pos));
        }
        return selectedLogs;
    }

    public void clearSelection() {
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    public void removeLogs(List<AlertLog> logsToRemove) {
        alertLogs.removeAll(logsToRemove);
        clearSelection();
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }

    @Override
    public int getItemCount() {
        return alertLogs.size();
    }

    static class AlertLogViewHolder extends RecyclerView.ViewHolder {
        TextView txtDateTime, txtLocation;
        ImageView btnPlay;

        public AlertLogViewHolder(@NonNull View itemView) {
            super(itemView);
            txtDateTime = itemView.findViewById(R.id.txtDateTime);
            txtLocation = itemView.findViewById(R.id.txtLocation);
            btnPlay = itemView.findViewById(R.id.btnPlay);
        }
    }
}