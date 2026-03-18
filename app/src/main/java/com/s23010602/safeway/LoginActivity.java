package com.s23010602.safeway;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;

/**
 * LoginActivity handles user authentication using Firebase Auth.
 * It allows users to sign in with their email (username field) and password.
 *
 * Reason for this class:
 * - Central entry point for returning users.
 * - Validates credentials against Firebase and opens HomeActivity on success.
 */
public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth; // FirebaseAuth instance for authentication
    private EditText usernameEditText, passwordEditText; // Input fields for email & password
    private Button loginButtonLoginPage; // Login button to trigger sign-in

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login); // Load the layout for login screen

        // Initialize FirebaseAuth instance
        mAuth = FirebaseAuth.getInstance();

        // Reference input fields and button from layout
        usernameEditText = findViewById(R.id.usernameEditText); // This field actually stores the email
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButtonLoginPage = findViewById(R.id.loginButtonLoginPage);

        // Set click listener for the login button
        loginButtonLoginPage.setOnClickListener(v -> {
            String email = usernameEditText.getText().toString().trim(); // Get email input
            String password = passwordEditText.getText().toString().trim(); // Get password input

            // Firebase sign-in with email and password
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Login successful
                            Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(this, HomeActivity.class)); // Go to HomeActivity
                            finish(); // Close LoginActivity
                        } else {
                            // Login failed — show error from Firebase
                            Toast.makeText(this, "Login failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }
}
