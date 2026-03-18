package com.s23010602.safeway;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.*;

/**
 * PRODUCTION READY: Displays a combined, sorted timeline of SOS alerts.
 * Uses efficient sorting and handles empty states for a professional feel.
 */
public class SosAlertsActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private SosAlertAdapter adapter;
    private ProgressBar loadingSpinner;
    private TextView emptyStateText;

    // Use a Map for O(1) deduplication by Alert ID
    private final Map<String, SosAlertAdapter.AlertItem> itemMap = new HashMap<>();
    private final List<SosAlertAdapter.AlertItem> displayList = new ArrayList<>();

    private DatabaseReference receivedRef, sentRef;
    private ChildEventListener receivedListener;
    private ValueEventListener sentListener;

    private String myUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sos_alerts);

        // UI Initialization
        recycler = findViewById(R.id.recyclerSosAlerts);
        loadingSpinner = findViewById(R.id.loadingSpinner); // Ensure this exists in your XML
        emptyStateText = findViewById(R.id.emptyStateText); // Ensure this exists in your XML

        recycler.setLayoutManager(new LinearLayoutManager(this));

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }
        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        adapter = new SosAlertAdapter(displayList, item -> {
            if (item.lat != null && item.lng != null) {
                Intent map = new Intent(this, MapActivity.class);
                map.putExtra("open_lat", item.lat);
                map.putExtra("open_lng", item.lng);
                map.putExtra("sender_name", item.displayName);
                startActivity(map);
            } else {
                Toast.makeText(this, "Location coordinates not available", Toast.LENGTH_SHORT).show();
            }
        });
        recycler.setAdapter(adapter);

        // Load data
        loadAlertData();
    }

    private void loadAlertData() {
        if (loadingSpinner != null) loadingSpinner.setVisibility(View.VISIBLE);

        // 1. Load History (Sent by you)
        attachSentListener();

        // 2. Load Real-time (Received from friends)
        attachReceivedListener();
    }

    private void attachReceivedListener() {
        receivedRef = FirebaseDatabase.getInstance().getReference("sos_alerts").child(myUid);
        receivedListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String prev) {
                processSnapshot(snapshot, false);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String prev) {
                processSnapshot(snapshot, false);
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                String id = snapshot.getKey();
                if (id != null) {
                    itemMap.remove(id);
                    updateUI();
                }
            }

            @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        receivedRef.addChildEventListener(receivedListener);
    }

    private void attachSentListener() {
        sentRef = FirebaseDatabase.getInstance().getReference("users").child(myUid).child("sos_history");
        sentListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    processSnapshot(child, true);
                }
                if (loadingSpinner != null) loadingSpinner.setVisibility(View.GONE);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                if (loadingSpinner != null) loadingSpinner.setVisibility(View.GONE);
            }
        };
        sentRef.addListenerForSingleValueEvent(sentListener);
    }

    private void processSnapshot(DataSnapshot snapshot, boolean isSentByMe) {
        String id = snapshot.getKey();
        if (id == null) return;

        // Sent items get a prefix to prevent ID collisions with Received items
        String finalId = isSentByMe ? "sent_" + id : id;

        Long ts = snapshot.child("timestamp").getValue(Long.class);
        long time = (ts != null) ? ts : System.currentTimeMillis();

        Double lat = toDouble(snapshot.child("lat").getValue());
        if (lat == null) lat = toDouble(snapshot.child("latitude").getValue());

        Double lng = toDouble(snapshot.child("lng").getValue());
        if (lng == null) lng = toDouble(snapshot.child("longitude").getValue());

        String senderUid = isSentByMe ? myUid : snapshot.child("fromUid").getValue(String.class);
        if (senderUid == null) senderUid = snapshot.child("uid").getValue(String.class);

        String initialName = isSentByMe ? "You" : getShortUid(senderUid);

        SosAlertAdapter.AlertItem item = new SosAlertAdapter.AlertItem(
                finalId, senderUid, initialName, time, lat, lng, isSentByMe
        );

        // Add to map and trigger UI update
        itemMap.put(finalId, item);

        // If it's a received alert, try to fetch the real name
        if (!isSentByMe && senderUid != null) {
            fetchRealName(senderUid, finalId);
        }

        updateUI();
    }

    private void fetchRealName(String uid, String mapKey) {
        FirebaseDatabase.getInstance().getReference("users").child(uid)
                .child("displayName").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String name = snapshot.getValue(String.class);
                        if (!TextUtils.isEmpty(name)) {
                            SosAlertAdapter.AlertItem cached = itemMap.get(mapKey);
                            if (cached != null) {
                                cached.displayName = name;
                                updateUI();
                            }
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private synchronized void updateUI() {
        runOnUiThread(() -> {
            displayList.clear();
            displayList.addAll(itemMap.values());
            Collections.sort(displayList, (a, b) -> Long.compare(b.timestamp, a.timestamp));

            // CHANGE THIS LINE:
            adapter.updateList(displayList); // This uses the method in the adapter!

            if (emptyStateText != null) {
                emptyStateText.setVisibility(displayList.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    private String getShortUid(String uid) {
        if (TextUtils.isEmpty(uid)) return "Friend";
        return uid.length() > 6 ? uid.substring(0, 6) : uid;
    }

    private Double toDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) {
            try { return Double.parseDouble((String) o); } catch (Exception e) { return null; }
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receivedRef != null && receivedListener != null) {
            receivedRef.removeEventListener(receivedListener);
        }
        if (sentRef != null && sentListener != null) {
            sentRef.removeEventListener(sentListener);
        }
    }
}