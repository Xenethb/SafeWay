package com.s23010602.safeway;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * SplashActivity:
 * Acts as the app entry point.
 * Determines if a user is already logged in and redirects accordingly.
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get currently logged-in user (if any)
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            // User is already logged in, go to HomeActivity
            startActivity(new Intent(this, HomeActivity.class));
        } else {
            // No user logged in, go to MainActivity (Login/SignUp options)
            startActivity(new Intent(this, MainActivity.class));
        }

        finish(); // Close SplashActivity so user can't return here with back button
    }
}
