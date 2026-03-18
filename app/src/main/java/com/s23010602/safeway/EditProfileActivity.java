package com.s23010602.safeway;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide; // Library for loading images from URL
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.ActivityResult; // needed for result object


public class EditProfileActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1; // Request code for image picker intent

    // UI elements
    private EditText editUsername, editEmail, editPhone;
    private Button btnEdit;
    private ImageView profileImage;
    private TextView changePictureText;

    // Image selection
    private Uri imageUri;

    // Firebase references
    private DatabaseReference userRef;
    private StorageReference storageRef;
    private String uid;

    private ActivityResultLauncher<Intent> imagePickerLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        // Initialize views
        editUsername = findViewById(R.id.edit_username);
        editEmail = findViewById(R.id.edit_email);
        editPhone = findViewById(R.id.edit_phone);
        btnEdit = findViewById(R.id.btn_edit);
        profileImage = findViewById(R.id.profile_image);
        changePictureText = findViewById(R.id.change_picture_text);


        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedImageUri = result.getData().getData();
                        // Do something with the selected image URI, e.g., display in ImageView
                    }
                }
        );


        // Get the currently logged-in Firebase user
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) { // If no user is logged in, exit activity
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Store UID of logged-in user
        uid = currentUser.getUid();

        // Reference to user's data in Realtime Database
        userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

        // Reference to Firebase Storage folder for profile images
        storageRef = FirebaseStorage.getInstance().getReference("profile_images");

        // Load current profile info from database
        loadUserProfile();

        // Click listener to change profile picture
        changePictureText.setOnClickListener(v -> openImagePicker());

        // Click listener to save profile changes
        btnEdit.setOnClickListener(v -> saveChanges());
    }

    // Open gallery to select a new profile picture
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent); // <-- use the new launcher
    }


    // Handle result from image picker (user selecting an image)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            imageUri = data.getData(); // Store selected image URI
            profileImage.setImageURI(imageUri); // Preview image before upload
        }
    }

    // Load profile details (username, email, phone, profile image) from Firebase Database
    private void loadUserProfile() {
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Get stored values from DB
                String username = snapshot.child("username").getValue(String.class);
                String email = snapshot.child("email").getValue(String.class);
                String phone = snapshot.child("phoneNumber").getValue(String.class);
                String profileImageUrl = snapshot.child("profileImageUrl").getValue(String.class);

                // Display values in input fields
                editUsername.setText(username);
                editEmail.setText(email);
                editPhone.setText(phone);

                // If profile image exists, load it with Glide
                if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                    Glide.with(EditProfileActivity.this)
                            .load(profileImageUrl)
                            .placeholder(R.drawable.ic_user) // Default image if none
                            .into(profileImage);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(EditProfileActivity.this, "Failed to load profile", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Save updated user details to Firebase
    private void saveChanges() {
        String newUsername = editUsername.getText().toString().trim();
        String newEmail = editEmail.getText().toString().trim();
        String newPhone = editPhone.getText().toString().trim();

        // Validation
        if (newUsername.isEmpty() || newEmail.isEmpty()) {
            Toast.makeText(this, "Username and Email cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        // Update fields in Realtime Database
        userRef.child("username").setValue(newUsername);
        userRef.child("email").setValue(newEmail);
        userRef.child("phoneNumber").setValue(newPhone);

        // If a new profile picture was selected
        if (imageUri != null) {
            StorageReference fileRef = storageRef.child(uid + ".jpg");
            fileRef.putFile(imageUri).addOnSuccessListener(taskSnapshot ->
                    fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        // Save image URL to database
                        userRef.child("profileImageUrl").setValue(uri.toString());
                        Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
                        finish();
                    })
            ).addOnFailureListener(e ->
                    Toast.makeText(this, "Image upload failed", Toast.LENGTH_SHORT).show()
            );
        } else {
            // If no new image selected, just save text changes
            Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}


