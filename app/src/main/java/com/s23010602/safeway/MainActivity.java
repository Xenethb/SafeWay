package com.s23010602.safeway;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * MainActivity is the entry point of the app.
 * It presents the user with options to Login or Sign up,
 * but automatically redirects authenticated users to the Home screen.
 */
public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // UI references for navigation
        Button loginButton = findViewById(R.id.loginButton);
        MaterialButton signupButton = findViewById(R.id.signupButton);

        // -----------------------------
        // Login button click listener
        // -----------------------------
        loginButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        // -----------------------------
        // Signup button click listener
        // -----------------------------
        signupButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CreateAccountActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is already signed in (non-null)
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // User is already logged in, bypass the gateway and go to Home
            Intent intent = new Intent(MainActivity.this, HomeActivity.class);
            startActivity(intent);
            // finish() ensures the user doesn't come back to this screen when pressing 'Back'
            finish();
        }
    }
}