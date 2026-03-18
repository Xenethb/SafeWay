package com.s23010602.safeway;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MapActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String TAG = "MapActivity";

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private AutoCompleteTextView friendSearch;
    private ArrayAdapter<String> searchAdapter;

    private final Map<String, Marker> friendMarkers = new HashMap<>();
    private final Map<String, String> friendNames   = new HashMap<>();
    private final Map<String, String> nameToUid     = new HashMap<>();
    private final Map<String, ValueEventListener> locationListeners = new HashMap<>();

    private ChildEventListener friendsChildListener;
    private DatabaseReference friendsRef;

    private Double pendingOpenLat = null;
    private Double pendingOpenLng = null;
    private String pendingFriendUid = null;
    private Marker alertMarker = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        friendSearch = findViewById(R.id.friendSearch);
        searchAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        friendSearch.setAdapter(searchAdapter);

        friendSearch.setOnItemClickListener((parent, view, position, id) -> {
            String label = searchAdapter.getItem(position);
            String uid = nameToUid.get(label);
            Marker marker = friendMarkers.get(uid);
            if (marker != null && mMap != null) {
                marker.showInfoWindow();
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 16f));
            }
        });

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFrag = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFrag != null) mapFrag.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);

        loadFriendsAndListen();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableUserLocation();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }

        handleOpenCoordsIntent(getIntent());
    }

    private void enableUserLocation() {
        try {
            if (mMap != null) {
                mMap.setMyLocationEnabled(true); // Native Blue Dot
            }
        } catch (SecurityException ignored) {}

        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                // Initially center the camera on the user
                if (loc != null && mMap != null && pendingOpenLat == null) {
                    LatLng pos = new LatLng(loc.getLatitude(), loc.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));
                    // Stop centering once found to allow user to scroll freely
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                }
            }
        };

        startLocationUpdates();
    }

    private void startLocationUpdates() {
        // MODERN API: Using Builder instead of .create()
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
        }
    }

    private void handleOpenCoordsIntent(Intent intent) {
        if (intent == null || !intent.hasExtra("open_lat")) return;

        double lat = intent.getDoubleExtra("open_lat", 0);
        double lng = intent.getDoubleExtra("open_lng", 0);
        String friendUid = intent.getStringExtra("friendUid");

        if (lat != 0 && lng != 0) {
            if (mMap != null) {
                addOrUpdateAlertMarker(lat, lng, friendUid);
            } else {
                pendingOpenLat = lat; pendingOpenLng = lng; pendingFriendUid = friendUid;
            }
        }
    }

    private void addOrUpdateAlertMarker(double lat, double lng, String friendUid) {
        LatLng pos = new LatLng(lat, lng);
        String title = getString(R.string.map_alert_location);

        if (friendUid != null) {
            String name = friendNames.get(friendUid);
            if (name != null) title = name;
        }

        if (alertMarker != null) alertMarker.remove();

        alertMarker = mMap.addMarker(new MarkerOptions().position(pos).title(title));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f));
    }

    private void loadFriendsAndListen() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        String myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        friendsRef = FirebaseDatabase.getInstance().getReference("friends").child(myUid);

        if (friendsChildListener != null) return;

        friendsChildListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String prev) {
                final String friendUid = snapshot.getKey();
                if (friendUid == null) return;

                FirebaseDatabase.getInstance().getReference("users").child(friendUid)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot ds) {
                                String name = ds.child("displayName").getValue(String.class);
                                if (name == null) name = ds.child("username").getValue(String.class);
                                if (name == null) name = getString(R.string.map_unknown_user);

                                final String finalName = name;
                                final String label = finalName + " (" + friendUid.substring(0, Math.min(6, friendUid.length())) + ")";

                                friendNames.put(friendUid, finalName);
                                nameToUid.put(label, friendUid);

                                runOnUiThread(() -> {
                                    if (searchAdapter.getPosition(label) == -1) {
                                        searchAdapter.add(label);
                                        searchAdapter.notifyDataSetChanged();
                                    }
                                });

                                attachFriendLocationListener(friendUid, finalName);
                            }
                            @Override public void onCancelled(@NonNull DatabaseError e) {}
                        });
            }
            // ... (keep original onChildRemoved logic, it was solid)
            @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot s) { /* Your existing logic */ }
            @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        friendsRef.addChildEventListener(friendsChildListener);
    }

    private void attachFriendLocationListener(String uid, String name) {
        ValueEventListener l = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot ls) {
                Double lat = ls.child("lat").getValue(Double.class);
                Double lng = ls.child("lng").getValue(Double.class);
                Long ts = ls.child("timestamp").getValue(Long.class);

                if (lat != null && lng != null && mMap != null) {
                    LatLng pos = new LatLng(lat, lng);
                    String timeLabel = "";
                    if (ts != null) {
                        int mins = (int) (System.currentTimeMillis() - ts) / 60000;
                        timeLabel = " " + getString(R.string.map_last_updated, mins);
                    }

                    Marker m = friendMarkers.get(uid);
                    if (m == null) {
                        m = mMap.addMarker(new MarkerOptions().position(pos).title(name + timeLabel)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                        friendMarkers.put(uid, m);
                    } else {
                        m.setPosition(pos);
                        m.setTitle(name + timeLabel);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        FirebaseDatabase.getInstance().getReference("location_sharing").child(uid).addValueEventListener(l);
        locationListeners.put(uid, l);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (locationCallback != null) fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Detach all listeners to prevent memory leaks
        for (String uid : locationListeners.keySet()) {
            FirebaseDatabase.getInstance().getReference("location_sharing").child(uid).removeEventListener(locationListeners.get(uid));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableUserLocation();
        }
    }
}