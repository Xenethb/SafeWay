package com.s23010602.safeway;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ChangePasswordActivity extends AppCompatActivity {

    // Input field where the user enters their new password
    private EditText etNewPassword;

    // Button to trigger the password update
    private Button btnUpdatePassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);
        // Inflate the screen layout defined in activity_change_password.xml

        // Link Java variables to the corresponding UI elements in the layout
        etNewPassword = findViewById(R.id.etNewPassword);
        btnUpdatePassword = findViewById(R.id.btnUpdatePassword);

        // Handle click event when "Update Password" button is pressed
        btnUpdatePassword.setOnClickListener(v -> {
            // Get the entered password and trim extra spaces
            String newPassword = etNewPassword.getText().toString().trim();

            // Check if the password is valid (non-empty and at least 6 characters)
            if (TextUtils.isEmpty(newPassword) || newPassword.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return; // Stop execution if validation fails
            }

            // Get the currently logged-in Firebase user
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();


            //Checks that a user is logged in before trying to update their password
            if (user != null) {
                // Update the user's password in Firebase
                user.updatePassword(newPassword)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                // Password successfully updated
                                Toast.makeText(this, "Password updated", Toast.LENGTH_SHORT).show();
                                finish(); // Close this screen and return to the previous activity
                            } else {
                                // Something went wrong (e.g., weak password or network issue)
                                Toast.makeText(this, "Failed to update password", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });
    }
}
