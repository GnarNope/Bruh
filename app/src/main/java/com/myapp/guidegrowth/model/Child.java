package com.myapp.guidegrowth.model;

import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Model class for Child data
 */
public class Child {
    private String id;
    private String name;
    private String email;
    private String status;
    private long lastUpdated;
    private String parentId;
    // Add location-related fields
    private double latitude;
    private double longitude;
    private long lastLocationUpdate;

    // Empty constructor required for Firestore
    public Child() {
    }

    public Child(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.status = "online";
        this.lastUpdated = System.currentTimeMillis();
        this.lastLocationUpdate = 0;
        this.latitude = 0;
        this.longitude = 0;
    }

    /**
     * Create a Child object from a Firestore document
     * @param document The Firestore document
     * @return A new Child object or null if document doesn't contain required fields
     */
    public static Child fromDocument(DocumentSnapshot document) {
        if (document == null || !document.exists()) {
            return null;
        }

        String id = document.getId();
        String name = document.getString("name");
        String email = document.getString("email");

        if (name == null) {
            name = "Unknown Child";
        }

        Child child = new Child(id, name, email);

        // Optional fields
        if (document.contains("status")) {
            child.setStatus(document.getString("status"));
        }

        if (document.contains("lastUpdated")) {
            Long lastUpdated = document.getLong("lastUpdated");
            if (lastUpdated != null) {
                child.setLastUpdated(lastUpdated);
            }
        }

        if (document.contains("parentId")) {
            child.setParentId(document.getString("parentId"));
        } else if (document.contains("linkedUserId")) {
            // Use linkedUserId as parentId if parentId is not available
            child.setParentId(document.getString("linkedUserId"));
        }
        
        // Try to get location data from the document or a nested location object
        // First check if latitude and longitude are directly in document
        if (document.contains("latitude") && document.contains("longitude")) {
            Double lat = document.getDouble("latitude");
            Double lng = document.getDouble("longitude");
            if (lat != null) child.setLatitude(lat);
            if (lng != null) child.setLongitude(lng);
            
            if (document.contains("lastLocationUpdate")) {
                Long timestamp = document.getLong("lastLocationUpdate");
                if (timestamp != null) {
                    child.setLastLocationUpdate(timestamp);
                } else {
                    // Fallback to lastUpdated if lastLocationUpdate not available
                    child.setLastLocationUpdate(child.getLastUpdated());
                }
            }
        }

        return child;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
    
    // New location-related getters and setters
    public double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
    
    public long getLastLocationUpdate() {
        return lastLocationUpdate;
    }
    
    public void setLastLocationUpdate(long lastLocationUpdate) {
        this.lastLocationUpdate = lastLocationUpdate;
    }
}
