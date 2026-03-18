package com.s23010602.safeway;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

/**
 * Activity: MyProfileActivity
 * Purpose: Display the current user's profile info, allow editing, password change, and logout.
 * Data Source: Firebase Realtime Database under /users/{uid}
 */
public class MyProfileActivity extends AppCompatActivity {

    // 🧩 UI elements for displaying user info
    private TextView tvUsername, tvEmail, tvPhone;
    private ImageView profileImage;
    private Button btnEdit, btnChangePassword, btnLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_profile);

        // 🧠 Bind UI views
        tvUsername = findViewById(R.id.username);
        tvEmail = findViewById(R.id.email);
        tvPhone = findViewById(R.id.phoneNumber);
        profileImage = findViewById(R.id.profileImage);
        btnEdit = findViewById(R.id.editButton);
        btnChangePassword = findViewById(R.id.changePasswordButton);
        btnLogout = findViewById(R.id.logoutButton);

        // 🔄 Load user info from Firebase
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUid);

        // ✅ Single-value listener: we only need to load once for profile display
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Get User object (username, email, phoneNumber)
                User user = snapshot.getValue(User.class);
                if (user != null) {
                    // Display info in UI; fallback to "N/A" if null
                    tvUsername.setText(user.username != null ? user.username : "N/A");
                    tvEmail.setText(user.email != null ? user.email : "N/A");
                    tvPhone.setText(user.phoneNumber != null ? user.phoneNumber : "N/A");

                    // 👤 Load profile image using Glide for async loading and placeholder
                    String profileImageUrl = snapshot.child("profileImageUrl").getValue(String.class);
                    if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                        Glide.with(MyProfileActivity.this)
                                .load(profileImageUrl)
                                .placeholder(R.drawable.ic_user) // shown while loading
                                .into(profileImage);
                    }
                } else {
                    // User data missing — show a toast notification
                    Toast.makeText(MyProfileActivity.this, "User data not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Firebase read failed
                Toast.makeText(MyProfileActivity.this, "Failed to load profile", Toast.LENGTH_SHORT).show();
            }
        });

        // ✏️ Edit Profile button: opens EditProfileActivity
        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(MyProfileActivity.this, EditProfileActivity.class);
            startActivity(intent);
        });

        // 🔐 Change Password button: opens ChangePasswordActivity
        btnChangePassword.setOnClickListener(v -> {
            Intent intent = new Intent(MyProfileActivity.this, ChangePasswordActivity.class);
            startActivity(intent);
        });

        // 🚪 Log Out button: signs out the user and returns to MainActivity
        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();

            // Intent flags ensure back navigation cannot return to logged-in session
            Intent intent = new Intent(MyProfileActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            finish(); // Close current activity
        });
    }
}
