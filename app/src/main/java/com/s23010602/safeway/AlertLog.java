package com.s23010602.safeway;

import java.util.UUID;

/**
 * PRODUCTION READY: Enhanced data model for SOS logs.
 * Includes unique IDs and structured location data for mapping and sorting.
 */
public class AlertLog {

    private String id;
    private long timestamp;
    private String formattedDate; // Keep this for easy UI display
    private double latitude;
    private double longitude;
    private String videoPath;

    // Required empty constructor for Gson/Firebase
    public AlertLog() {
    }

    // Pro Constructor: Generates a unique ID automatically
    public AlertLog(long timestamp, String formattedDate, double latitude, double longitude, String videoPath) {
        this.id = UUID.randomUUID().toString();
        this.timestamp = timestamp;
        this.formattedDate = formattedDate;
        this.latitude = latitude;
        this.longitude = longitude;
        this.videoPath = videoPath;
    }

    // Getters
    public String getId() { return id; }
    public long getTimestamp() { return timestamp; }
    public String getFormattedDate() { return formattedDate; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getVideoPath() { return videoPath; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setFormattedDate(String formattedDate) { this.formattedDate = formattedDate; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public void setVideoPath(String videoPath) { this.videoPath = videoPath; }
}