package com.s23010602.safeway;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.*;

public class SosAlertAdapter extends RecyclerView.Adapter<SosAlertAdapter.VH> {

    public interface ClickCb { void onClick(AlertItem item); }

    public static class AlertItem {
        public final String id;
        public final String senderUid;
        public String displayName;
        public final long timestamp;
        public final Double lat, lng;
        public final boolean isSent;

        public AlertItem(String id, String senderUid, String displayName, long timestamp, Double lat, Double lng, boolean isSent) {
            this.id = id;
            this.senderUid = senderUid;
            this.displayName = displayName;
            this.timestamp = timestamp;
            this.lat = lat;
            this.lng = lng;
            this.isSent = isSent;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AlertItem item = (AlertItem) o;
            return timestamp == item.timestamp &&
                    isSent == item.isSent &&
                    Objects.equals(id, item.id) &&
                    Objects.equals(displayName, item.displayName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, displayName, timestamp, isSent);
        }
    }

    // FIXED: Removed redundant 'new ArrayList<>()' initializer
    private final List<AlertItem> list;
    private final ClickCb cb;
    private final SimpleDateFormat fmt = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());

    public SosAlertAdapter(List<AlertItem> items, ClickCb cb) {
        this.list = new ArrayList<>(items);
        this.cb = cb;
    }

    // This is used in SosAlertsActivity to trigger DiffUtil animations
    public void updateList(List<AlertItem> newList) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new AlertDiffCallback(this.list, newList));
        this.list.clear();
        this.list.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sos_alert, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        AlertItem it = list.get(position);
        Context context = holder.itemView.getContext();

        // FIXED: Using string resources with placeholders to satisfy linter
        if (it.isSent) {
            holder.tvName.setText(context.getString(R.string.sos_sent_by_you));
            holder.tvName.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            holder.tvName.setText(context.getString(R.string.sos_alert_from, it.displayName));
            holder.tvName.setTextColor(Color.parseColor("#C62828"));
        }

        holder.tvTime.setText(fmt.format(new Date(it.timestamp)));

        if (it.lat != null && it.lng != null) {
            // FIXED: Using string resources for location formatting
            holder.tvLocation.setText(context.getString(R.string.sos_location_format, it.lat, it.lng));
            holder.tvLocation.setVisibility(View.VISIBLE);
        } else {
            holder.tvLocation.setText(context.getString(R.string.sos_no_gps));
            holder.tvLocation.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (cb != null) cb.onClick(it);
        });
    }

    @Override public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvTime, tvLocation;
        VH(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tvAlertName);
            tvTime = v.findViewById(R.id.tvAlertTime);
            tvLocation = v.findViewById(R.id.tvAlertLocation);
        }
    }

    private static class AlertDiffCallback extends DiffUtil.Callback {
        private final List<AlertItem> oldList;
        private final List<AlertItem> newList;
        public AlertDiffCallback(List<AlertItem> oldList, List<AlertItem> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }
        @Override public int getOldListSize() { return oldList.size(); }
        @Override public int getNewListSize() { return newList.size(); }
        @Override public boolean areItemsTheSame(int o, int n) { return oldList.get(o).id.equals(newList.get(n).id); }
        @Override public boolean areContentsTheSame(int o, int n) { return oldList.get(o).equals(newList.get(n)); }
    }
}