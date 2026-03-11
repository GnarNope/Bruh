package com.myapp.guidegrowth.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class to optimize Firestore write operations
 * - Implements caching to prevent redundant writes
 * - Throttles write frequency
 * - Implements differential updates
 */
public class FirestoreWriteOptimizer {
    private static final String TAG = "FirestoreWriteOptimizer";
    private static final String PREFS_NAME = "firestoreWriteCache";
    private static final long MIN_WRITE_INTERVAL_MS = 15 * 60 * 1000; // 15 minutes minimum between writes (increased)
    
    // In-memory cache to avoid loading from SharedPreferences repeatedly
    private static final Map<String, String> dataHashCache = new ConcurrentHashMap<>();
    private static final Map<String, Long> locationUpdateTimestamps = new ConcurrentHashMap<>();
    
    private static FirestoreWriteOptimizer instance;
    private final Context context;
    private final SharedPreferences preferences;
    private final Map<String, Long> lastWriteTimestamps = new ConcurrentHashMap<>();
    
    private FirestoreWriteOptimizer(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Load cached hashes during initialization to improve performance
        Map<String, ?> allPrefs = preferences.getAll();
        for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
            if (entry.getKey().endsWith("_hash") && entry.getValue() instanceof String) {
                dataHashCache.put(entry.getKey(), (String)entry.getValue());
            }
        }
    }
    
    public static synchronized FirestoreWriteOptimizer getInstance(Context context) {
        if (instance == null) {
            instance = new FirestoreWriteOptimizer(context);
        }
        return instance;
    }
    
    /**
     * Optimized write method that prevents redundant writes and throttles operations
     */
    public void optimizedWrite(DocumentReference documentRef, Map<String, Object> data, 
                              String cacheKey, boolean forceWrite, WriteCallback callback) {
        if (documentRef == null || data == null) {
            if (callback != null) callback.onWriteFailure("Invalid document reference or data");
            return;
        }
        
        // Create a new map to avoid modifying the input data
        Map<String, Object> dataToWrite = new HashMap<>(data);
        
        // Get cached data hash from memory cache first, then preferences
        String cachedDataHash = dataHashCache.getOrDefault(cacheKey + "_hash", 
                                preferences.getString(cacheKey + "_hash", ""));
        String currentDataHash = generateDataHash(dataToWrite);
        
        // Check if data has changed
        boolean dataChanged = !cachedDataHash.equals(currentDataHash);
        
        // Check if enough time has passed since last write
        long lastWriteTime = lastWriteTimestamps.getOrDefault(cacheKey, 0L);
        long currentTime = System.currentTimeMillis();
        boolean timeThresholdMet = (currentTime - lastWriteTime) >= MIN_WRITE_INTERVAL_MS;
        
        // Only write if data changed or enough time has passed, or force write is true
        if (dataChanged || timeThresholdMet || forceWrite) {
            // Add timestamp if not present
            if (!dataToWrite.containsKey("timestamp")) {
                dataToWrite.put("timestamp", currentTime);
            }
            
            // Perform write operation
            documentRef.set(dataToWrite, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    // Update cache and timestamps
                    dataHashCache.put(cacheKey + "_hash", currentDataHash);
                    
                    preferences.edit()
                        .putString(cacheKey + "_hash", currentDataHash)
                        .putLong(cacheKey + "_time", currentTime)
                        .apply();
                    
                    lastWriteTimestamps.put(cacheKey, currentTime);
                    
                    Log.d(TAG, "Write successful for: " + cacheKey + 
                            (dataChanged ? " (data changed)" : "") +
                            (timeThresholdMet ? " (time threshold met)" : "") +
                            (forceWrite ? " (force write)" : ""));
                    
                    if (callback != null) {
                        callback.onWriteSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Write failed for: " + cacheKey, e);
                    if (callback != null) {
                        callback.onWriteFailure(e.getMessage());
                    }
                });
        } else {
            Log.d(TAG, "Write skipped for: " + cacheKey + " (no changes and time threshold not met)");
            if (callback != null) {
                callback.onWriteSkipped("No changes detected and time threshold not met");
            }
        }
    }
    
    /**
     * Optimized write method for location data - only writes on parent request
     */
    public void optimizedLocationWrite(DocumentReference documentRef, double latitude, double longitude,
                                     float accuracy, String provider, String cacheKey, boolean isParentRequested) {
        // Only write location on parent request or if significant change/time elapsed
        if (isParentRequested) {
            // Force write immediately if parent requested
            Map<String, Object> locationData = new HashMap<>();
            locationData.put("latitude", latitude);
            locationData.put("longitude", longitude);
            locationData.put("accuracy", accuracy);
            locationData.put("timestamp", System.currentTimeMillis());
            locationData.put("provider", provider);
            locationData.put("parentRequested", true);
            
            documentRef.set(locationData)
                .addOnSuccessListener(aVoid -> {
                    // Update cache
                    preferences.edit()
                        .putFloat(cacheKey + "_lat", (float)latitude)
                        .putFloat(cacheKey + "_lng", (float)longitude)
                        .putLong(cacheKey + "_time", System.currentTimeMillis())
                        .apply();
                    
                    locationUpdateTimestamps.put(cacheKey, System.currentTimeMillis());
                    
                    Log.d(TAG, "Location write successful (parent requested)");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Location write failed", e);
                });
        } else {
            // For regular updates, check if significant change has occurred
            double prevLatitude = preferences.getFloat(cacheKey + "_lat", 0);
            double prevLongitude = preferences.getFloat(cacheKey + "_lng", 0);
            
            // Get the last write time
            long lastUpdateTime = locationUpdateTimestamps.getOrDefault(cacheKey, 
                                preferences.getLong(cacheKey + "_time", 0));
            long currentTime = System.currentTimeMillis();
            
            // Only write if:
            // 1. Location changed by more than 100 meters (increased threshold)
            // 2. Or it's been at least 30 minutes (increased interval)
            boolean locationSignificantlyChanged = calculateDistance(prevLatitude, prevLongitude, latitude, longitude) > 100;
            boolean timeThresholdMet = (currentTime - lastUpdateTime) >= (30 * 60 * 1000); // 30 minutes
            
            if (locationSignificantlyChanged || timeThresholdMet) {
                Map<String, Object> locationData = new HashMap<>();
                locationData.put("latitude", latitude);
                locationData.put("longitude", longitude);
                locationData.put("accuracy", accuracy);
                locationData.put("timestamp", currentTime);
                locationData.put("provider", provider);
                
                documentRef.set(locationData)
                    .addOnSuccessListener(aVoid -> {
                        // Update cache
                        preferences.edit()
                            .putFloat(cacheKey + "_lat", (float)latitude)
                            .putFloat(cacheKey + "_lng", (float)longitude)
                            .putLong(cacheKey + "_time", currentTime)
                            .apply();
                        
                        locationUpdateTimestamps.put(cacheKey, currentTime);
                        
                        Log.d(TAG, "Location write successful" + 
                                (locationSignificantlyChanged ? " (location changed)" : "") +
                                (timeThresholdMet ? " (time threshold met)" : ""));
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Location write failed", e);
                    });
            } else {
                Log.d(TAG, "Location write skipped (no significant change and time threshold not met)");
            }
        }
    }
    
    /**
     * Calculate distance between two coordinates in meters using the Haversine formula
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        if (lat1 == 0 && lon1 == 0) return 10000; // Force update if previous location was 0,0
        
        final int R = 6371000; // Earth radius in meters
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
    
    /**
     * Generates a simple hash based on the data values
     */
    private String generateDataHash(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        // Sort keys to ensure consistent ordering
        for (String key : data.keySet().stream().sorted().toArray(String[]::new)) {
            Object value = data.get(key);
            // Skip timestamp fields for hash calculation to focus on content changes
            if (value != null && !key.contains("time") && !key.equals("timestamp")) {
                sb.append(key).append(":").append(value.toString()).append(";");
            }
        }
        return sb.toString();
    }
    
    /**
     * Clear cached data for testing or reset purposes
     */
    public void clearCache(String cacheKey) {
        preferences.edit()
            .remove(cacheKey + "_hash")
            .remove(cacheKey + "_time")
            .remove(cacheKey + "_lat")
            .remove(cacheKey + "_lng")
            .apply();
        
        // Also clear from in-memory cache
        dataHashCache.remove(cacheKey + "_hash");
        lastWriteTimestamps.remove(cacheKey);
        locationUpdateTimestamps.remove(cacheKey);
    }
    
    public interface WriteCallback {
        void onWriteSuccess();
        void onWriteFailure(String errorMessage);
        void onWriteSkipped(String reason);
    }
}
