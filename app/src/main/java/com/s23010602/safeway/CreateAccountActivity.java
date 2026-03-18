package com.s23010602.safeway;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class CreateAccountActivity extends AppCompatActivity {

    private static final String TAG = "FIREBASE_DEBUG"; // Used for logging debug info

    // Input fields for account details
    private EditText usernameEditText, emailEditText, passwordEditText, confirmPasswordEditText;

    // Buttons for navigation
    private Button nextButton, goToLoginButton;

    // Progress bar shown during account creation
    private ProgressBar progressBar;

    // Firebase authentication and database references
    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account); // Load UI from layout file

        // Initialize Firebase Auth and Database reference (pointing to "users" node)
        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");//points to the users node in realtime database


        // Link UI elements to variables
        usernameEditText = findViewById(R.id.usernameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        nextButton = findViewById(R.id.nextButton);
        goToLoginButton = findViewById(R.id.btnGoToLogin);
        progressBar = findViewById(R.id.progressBar);

        // When "Next" button is clicked → attempt account creation
        nextButton.setOnClickListener(v -> createAccount());

        // When "Go to Login" is clicked → open LoginActivity and close this one
        goToLoginButton.setOnClickListener(v -> {
            startActivity(new Intent(CreateAccountActivity.this, LoginActivity.class));
            finish();
        });
    }

    /**
     * Handles the account creation process:
     * 1. Validates inputs
     * 2. Creates user in Firebase Authentication
     * 3. Stores user profile in Firebase Realtime Database
     */
    private void createAccount() {
        // Get input values
        String username = usernameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();

        // Validate inputs (check required fields and password rules)
        if (TextUtils.isEmpty(username)) {
            usernameEditText.setError("Username is required");
            usernameEditText.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            emailEditText.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            passwordEditText.requestFocus();
            return;
        }
        if (password.length() < 6) {
            passwordEditText.setError("Password should be at least 6 characters");
            passwordEditText.requestFocus();
            return;
        }
        if (!password.equals(confirmPassword)) {
            confirmPasswordEditText.setError("Passwords do not match");
            confirmPasswordEditText.requestFocus();
            return;
        }

        // Show progress bar and disable button to prevent double clicks
        progressBar.setVisibility(View.VISIBLE);
        nextButton.setEnabled(false);

        Log.d(TAG, "Starting account creation for email: " + email);

        // Create user in Firebase Authentication with email + password
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    // Hide progress bar and re-enable button
                    progressBar.setVisibility(View.GONE);
                    nextButton.setEnabled(true);

                    if (task.isSuccessful()) {
                        // Authentication succeeded
                        FirebaseUser firebaseUser = task.getResult().getUser();
                        if (firebaseUser != null) {
                            String uid = firebaseUser.getUid(); // Unique Firebase UID

                            // Create a User object (your model class) to store in Realtime DB
                            User user = new User(uid, username, email, "");
                            // (Empty string here for phone number, can be filled later)

                            Log.d(TAG, "Auth success. Saving user to DB: " + email);

                            // Save user profile in Realtime Database under "users/{uid}"
                            usersRef.child(uid).setValue(user)
                                    .addOnCompleteListener(dbTask -> {
                                        if (dbTask.isSuccessful()) {
                                            // Successfully saved user profile
                                            Log.d(TAG, "✅ User saved to Realtime DB successfully for uid: " + uid);
                                            Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();

                                            // Go to HomeActivity after account creation
                                            startActivity(new Intent(CreateAccountActivity.this, HomeActivity.class));
                                            finish();
                                        } else {
                                            // Database save failed
                                            Log.e(TAG, "❌ Failed to save user to DB", dbTask.getException());
                                            Toast.makeText(this, "Database write failed: " + dbTask.getException().getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                    });
                        } else {
                            // Should not happen, but safety check
                            Log.e(TAG, "❌ Auth succeeded but user is null");
                            Toast.makeText(this, "Unexpected error: user is null", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        // Authentication failed (e.g., email already in use)
                        Exception e = task.getException();
                        if (e instanceof FirebaseAuthUserCollisionException) {
                            Log.w(TAG, "Email already in use");
                            Toast.makeText(this, "Email already registered", Toast.LENGTH_LONG).show();
                        } else {
                            Log.e(TAG, "Auth failed", e);
                            Toast.makeText(this, "Signup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}
