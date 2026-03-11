package com.myapp.guidegrowth.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to handle location-related functionality
 */
public class LocationHelper {
    private static final String TAG = "LocationHelper";
    
    /**
     * Request location update from a child device
     * This minimizes writes by only requesting when needed
     * 
     * @param childId The ID of the child to request location from
     * @param callback Callback with request result
     */
    public static void requestChildLocation(String childId, RequestLocationCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        Map<String, Object> commandData = new HashMap<>();
        commandData.put(CommandUtils.FIELD_TYPE, CommandUtils.CMD_REQUEST_LOCATION);
        commandData.put(CommandUtils.FIELD_MESSAGE, "Please share your location");
        commandData.put(CommandUtils.FIELD_TIMESTAMP, System.currentTimeMillis());
        commandData.put(CommandUtils.FIELD_STATUS, CommandUtils.STATUS_SENT);
        
        db.collection("children")
            .document(childId)
            .collection("commands")
            .document("latest")
            .set(commandData)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Location request sent to child: " + childId);
                if (callback != null) {
                    callback.onRequestSent();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error requesting location from child: " + childId, e);
                if (callback != null) {
                    callback.onRequestFailed(e.getMessage());
                }
            });
    }
    
    /**
     * Open the child's location in Google Maps
     * This doesn't require additional Firestore writes, just reads location and opens Maps
     * 
     * @param context Context
     * @param childId Child ID to get location for
     * @param callback Callback with results
     */
    public static void openChildLocationInMaps(Context context, String childId, OpenMapCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection("children")
            .document(childId)
            .collection("location")
            .document("current")
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    Double latitude = documentSnapshot.getDouble("latitude");
                    Double longitude = documentSnapshot.getDouble("longitude");
                    Long timestamp = documentSnapshot.getLong("timestamp");
                    
                    if (latitude != null && longitude != null) {
                        // Check if location data is recent (less than 10 minutes old)
                        long currentTime = System.currentTimeMillis();
                        boolean isRecent = timestamp != null && 
                                (currentTime - timestamp < 10 * 60 * 1000);
                        
                        if (!isRecent && callback != null) {
                            callback.onLocationOutdated();
                            // Continue with opening maps anyway with available location
                        }
                        
                        // Open Google Maps with the location
                        String uri = "geo:" + latitude + "," + longitude + "?q=" + latitude + "," + longitude + "(Child Location)";
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                        intent.setPackage("com.google.android.apps.maps");
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        
                        // Check if Google Maps is installed
                        if (intent.resolveActivity(context.getPackageManager()) != null) {
                            context.startActivity(intent);
                            if (callback != null) {
                                callback.onMapOpened();
                            }
                        } else {
                            // Google Maps not installed, open in browser instead
                            String browserUri = "https://www.google.com/maps?q=" + latitude + "," + longitude;
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(browserUri));
                            browserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(browserIntent);
                            
                            if (callback != null) {
                                callback.onMapOpened();
                            }
                        }
                    } else {
                        if (callback != null) {
                            callback.onLocationNotAvailable("Location coordinates not available");
                        }
                    }
                } else {
                    if (callback != null) {
                        callback.onLocationNotAvailable("Location data not found");
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error getting child location: " + childId, e);
                if (callback != null) {
                    callback.onLocationNotAvailable("Error: " + e.getMessage());
                }
            });
    }
    
    /**
     * Check if location data is available for a child
     * 
     * @param childId Child ID to check
     * @param callback Callback with result
     */
    public static void checkChildLocationAvailability(String childId, LocationAvailabilityCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection("children")
            .document(childId)
            .collection("location")
            .document("current")
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    Double latitude = documentSnapshot.getDouble("latitude");
                    Double longitude = documentSnapshot.getDouble("longitude");
                    Long timestamp = documentSnapshot.getLong("timestamp");
                    
                    if (latitude != null && longitude != null) {
                        // Check if location data is recent (less than 10 minutes old)
                        long currentTime = System.currentTimeMillis();
                        boolean isRecent = timestamp != null && 
                                (currentTime - timestamp < 10 * 60 * 1000);
                        
                        callback.onLocationAvailabilityResult(true, isRecent, timestamp);
                    } else {
                        callback.onLocationAvailabilityResult(false, false, null);
                    }
                } else {
                    callback.onLocationAvailabilityResult(false, false, null);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error checking location availability: " + childId, e);
                callback.onLocationCheckFailed(e.getMessage());
            });
    }
    
    public interface RequestLocationCallback {
        void onRequestSent();
        void onRequestFailed(String errorMessage);
    }
    
    public interface OpenMapCallback {
        void onMapOpened();
        void onLocationNotAvailable(String reason);
        void onLocationOutdated();
    }
    
    public interface LocationAvailabilityCallback {
        void onLocationAvailabilityResult(boolean isAvailable, boolean isRecent, Long timestamp);
        void onLocationCheckFailed(String errorMessage);
    }
}
