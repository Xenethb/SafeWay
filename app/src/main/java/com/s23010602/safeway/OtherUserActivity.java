package com.s23010602.safeway;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.HashMap;
import java.util.Map;

/**
 * PRODUCTION READY: Manages user-to-user connections.
 * Implements two-way friendship logic to ensure mutual safety monitoring.
 */
public class OtherUserActivity extends AppCompatActivity {

    private static final String TAG = "OtherUserActivity";
    private static final String EXTRA_OTHER_UID = "other_uid";

    private Button btnAddRemove;
    private TextView tvUsername, tvEmail, tvPhone;
    private String otherUid, currentUid;
    private boolean isFriend = false;

    public static Intent makeIntent(Context ctx, String otherUid) {
        Intent i = new Intent(ctx, OtherUserActivity.class);
        i.putExtra(EXTRA_OTHER_UID, otherUid);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_other_user);

        // UI Refs
        tvUsername = findViewById(R.id.other_username);
        tvEmail    = findViewById(R.id.other_email);
        tvPhone    = findViewById(R.id.other_phone);
        btnAddRemove = findViewById(R.id.btn_add_remove_friend);

        otherUid = getIntent().getStringExtra(EXTRA_OTHER_UID);
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        if (otherUid == null || currentUid == null) {
            Toast.makeText(this, "User error", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        fetchOtherUserInfo();
        checkFriendshipStatus();

        btnAddRemove.setOnClickListener(v -> toggleFriendship());
    }

    private void fetchOtherUserInfo() {
        FirebaseDatabase.getInstance().getReference("users").child(otherUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        User u = snapshot.getValue(User.class);
                        if (u != null) {
                            tvUsername.setText(u.username);
                            tvEmail.setText(u.email);
                            tvPhone.setText(u.phoneNumber != null ? u.phoneNumber : "N/A");
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void checkFriendshipStatus() {
        // We check our own friend list to see if this user exists
        FirebaseDatabase.getInstance().getReference("friends")
                .child(currentUid).child(otherUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        isFriend = snapshot.exists();
                        updateButtonUI();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void toggleFriendship() {
        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();

        // Use a Map for an Atomic Update (Professional Standard)
        Map<String, Object> updates = new HashMap<>();

        if (isFriend) {
            // Remove from both lists
            updates.put("/friends/" + currentUid + "/" + otherUid, null);
            updates.put("/friends/" + otherUid + "/" + currentUid, null);
            Toast.makeText(this, "Friend removed", Toast.LENGTH_SHORT).show();
        } else {
            // Add to both lists
            updates.put("/friends/" + currentUid + "/" + otherUid, true);
            updates.put("/friends/" + otherUid + "/" + currentUid, true);
            Toast.makeText(this, "Friend added!", Toast.LENGTH_SHORT).show();
        }

        rootRef.updateChildren(updates).addOnFailureListener(e ->
                Log.e(TAG, "Friendship update failed", e));
    }

    private void updateButtonUI() {
        btnAddRemove.setText(isFriend ? "Remove Friend" : "Add as Friend");
        // Optional: Change color based on state
        btnAddRemove.setBackgroundColor(isFriend ? 0xFFB00020 : 0xFF2E7D32); // Red for remove, Green for add
    }
}