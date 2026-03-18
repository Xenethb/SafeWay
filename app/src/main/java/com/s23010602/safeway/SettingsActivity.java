package com.s23010602.safeway;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class SettingsActivity extends AppCompatActivity {

    // 🔹 Buttons for different settings actions
    private Button btnDeleteAccount, btnFeedback, btnPrivacyPolicy;

    // 🔹 FirebaseAuth instance to handle user authentication
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 🔹 Initialize FirebaseAuth
        mAuth = FirebaseAuth.getInstance();

        // 🔹 Find buttons in layout
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);
        btnFeedback = findViewById(R.id.btnFeedback);
        btnPrivacyPolicy = findViewById(R.id.btnPrivacyPolicy);

        // 🔹 Set click listener for "Delete Account" button
        btnDeleteAccount.setOnClickListener(v -> confirmDeleteAccount());

        // 🔹 Set click listener for "Feedback" button
        btnFeedback.setOnClickListener(v -> sendFeedbackEmail());

        // 🔹 Set click listener for "Privacy Policy" button
        btnPrivacyPolicy.setOnClickListener(v -> openPrivacyPolicy());
    }

    /**
     * 🔹 Show confirmation dialog before deleting account.
     * Ensures the user doesn't accidentally delete their account.
     */
    private void confirmDeleteAccount() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
                .setPositiveButton("Yes, Delete", (dialog, which) -> deleteAccount()) // If confirmed, delete account
                .setNegativeButton("Cancel", null) // Cancel does nothing
                .show();
    }

    /**
     * 🔹 Deletes the current Firebase user account.
     * Shows a toast for success/failure and closes the activity after deletion.
     */
    private void deleteAccount() {
        if (mAuth.getCurrentUser() != null) {
            mAuth.getCurrentUser().delete()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Account deleted", Toast.LENGTH_SHORT).show();
                            finish(); // Or redirect to login screen
                        } else {
                            Toast.makeText(this, "Failed to delete account: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        }
    }

    /**
     * 🔹 Opens an email app to send feedback.
     * Uses ACTION_SENDTO with mailto: scheme to launch email client.
     */
    private void sendFeedbackEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("s23010602.com"));
        intent.putExtra(Intent.EXTRA_SUBJECT, "SafeWay App Feedback");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent); // Launch email app if available
        } else {
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show(); // Fallback message
        }
    }

    /**
     * 🔹 Opens the privacy policy link in a browser.
     * Uses ACTION_VIEW with the policy URL.
     */
    private void openPrivacyPolicy() {
        String url = "https://website.com/privacy-policy";  //put tge website link
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }
}

