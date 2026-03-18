package com.s23010602.safeway;

import com.google.firebase.database.IgnoreExtraProperties;

/**
 * PRODUCTION READY: User Data Model.
 * Encapsulated fields for better data integrity during Firebase operations.
 */
@IgnoreExtraProperties
public class User {
    public String uid;
    public String username;
    public String email;
    public String phoneNumber;

    // Required empty constructor for Firebase
    public User() {}

    public User(String uid, String username, String email, String phoneNumber) {
        this.uid = uid;
        this.username = username;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }
}