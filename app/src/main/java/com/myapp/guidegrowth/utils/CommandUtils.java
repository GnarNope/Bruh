package com.myapp.guidegrowth.utils;

import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for handling parent-child commands
 */
public class CommandUtils {
    private static final String TAG = "CommandUtils";
    
    // Command types
    public static final String CMD_CHECK_IN = "check_in";
    public static final String CMD_REQUEST_LOCATION = "request_location";
    public static final String CMD_LOCK_DEVICE = "lock_device";
    public static final String CMD_UNLOCK_DEVICE = "unlock_device";
    public static final String CMD_SET_LIMITS = "set_limits";
    public static final String CMD_WARNING = "warning";
    public static final String CMD_OPEN_URL = "open_url";
    
    // Command status values
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_RECEIVED = "received";
    public static final String STATUS_PROCESSED = "processed";
    public static final String STATUS_FAILED = "failed";
    
    // Command fields
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_MESSAGE = "message";
    public static final String FIELD_TIMESTAMP = "timestamp";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_STATUS_DETAIL = "statusDetail";
    public static final String FIELD_URL = "url";
    public static final String FIELD_LIMITS = "limits";
    public static final String FIELD_ERROR = "error";
    
    // Additional fields for specific commands
    public static final String FIELD_APP_LIMITS = "appLimits";
    public static final String FIELD_SCREEN_TIME_LIMIT = "screenTimeLimit";
    
    /**
     * Create a command data map
     */
    public static Map<String, Object> createCommand(String commandType, String message) {
        Map<String, Object> command = new HashMap<>();
        command.put(FIELD_TYPE, commandType);
        command.put(FIELD_MESSAGE, message);
        command.put(FIELD_TIMESTAMP, System.currentTimeMillis());
        command.put(FIELD_STATUS, STATUS_SENT);
        return command;
    }
    
    /**
     * Create a specific command for requesting usage stats update
     */
    public static Map<String, Object> createUsageStatsCommand() {
        Map<String, Object> command = new HashMap<>();
        command.put(FIELD_TYPE, "check_in");
        command.put(FIELD_MESSAGE, "Request for latest usage stats");
        command.put(FIELD_TIMESTAMP, System.currentTimeMillis());
        command.put(FIELD_STATUS, STATUS_SENT);
        command.put("updateType", "usage_stats");  // Specify what type of update we want
        command.put("priority", "high");           // Mark it as high priority
        return command;
    }
    
    /**
     * Update the status of a command without creating a new document
     * Modified to use a direct update to prevent loop feedback
     */
    public static void updateCommandStatus(String childId, String status, String statusDetail) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference commandRef = db.collection("children")
                .document(childId).collection("commands").document("latest");
        
        Map<String, Object> updates = new HashMap<>();
        updates.put(FIELD_STATUS, status);
        
        if (statusDetail != null) {
            updates.put(FIELD_STATUS_DETAIL, statusDetail);
        }
        
        // Add a timestamp to track when the status was updated
        updates.put("statusUpdatedAt", System.currentTimeMillis());
        
        // Use set with merge to avoid triggering the snapshot listener too aggressively
        commandRef.set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Command status updated: " + status))
                .addOnFailureListener(e -> Log.e(TAG, "Error updating command status", e));
    }
    
    /**
     * Add this method to directly update a command status with a single write
     */
    public static void directUpdateCommandStatus(String childId, Map<String, Object> statusUpdates) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference commandRef = db.collection("children")
                .document(childId).collection("commands").document("latest");
        
        // Add timestamp
        statusUpdates.put("statusUpdatedAt", System.currentTimeMillis());
        
        // Use update instead of set to avoid replacing the entire document
        commandRef.update(statusUpdates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Command status directly updated"))
                .addOnFailureListener(e -> Log.e(TAG, "Error directly updating command status", e));
    }
    
    /**
     * Send a general command to a child
     * 
     * @param childId The child's ID
     * @param commandType The type of command
     * @param data Additional data for the command
     * @param writeOptimizer FirestoreWriteOptimizer instance
     * @param callback Callback for write result
     */
    public static void sendCommand(String childId, String commandType, Map<String, Object> data,
                                 FirestoreWriteOptimizer writeOptimizer, 
                                 FirestoreWriteOptimizer.WriteCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        Map<String, Object> commandData = createCommand(commandType, 
                data.containsKey(FIELD_MESSAGE) ? (String)data.get(FIELD_MESSAGE) : "");
        
        // Add any additional data fields
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!commandData.containsKey(entry.getKey())) {
                commandData.put(entry.getKey(), entry.getValue());
            }
        }
        
        DocumentReference commandRef = db.collection("children")
                .document(childId)
                .collection("commands")
                .document("latest");
        
        // Use the optimizer to avoid redundant writes
        writeOptimizer.optimizedWrite(
                commandRef,
                commandData,
                "command_" + childId,
                true, // Force command writes
                callback
        );
    }
    
    /**
     * Send a notification command to child
     */
    public static void sendNotificationToChild(String childId, String message, CommandCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        Map<String, Object> command = createCommand(CMD_WARNING, message);
        
        db.collection("children")
                .document(childId)
                .collection("commands")
                .document("latest")
                .set(command)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Notification sent to child: " + childId);
                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error sending notification to child", e);
                    if (callback != null) {
                        callback.onFailure(e.getMessage());
                    }
                });
    }
    
    /**
     * Send a device lock command to child
     */
    public static void sendLockDeviceCommand(String childId, CommandCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        Map<String, Object> command = createCommand(CMD_LOCK_DEVICE, "Device locked by parent");
        
        db.collection("children")
                .document(childId)
                .collection("commands")
                .document("latest")
                .set(command)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Lock command sent to child: " + childId);
                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error sending lock command to child", e);
                    if (callback != null) {
                        callback.onFailure(e.getMessage());
                    }
                });
    }
    
    /**
     * Send an unlock device command to child
     */
    public static void sendUnlockDeviceCommand(String childId, CommandCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        Map<String, Object> command = createCommand(CMD_UNLOCK_DEVICE, "Device unlocked by parent");
        
        db.collection("children")
                .document(childId)
                .collection("commands")
                .document("latest")
                .set(command)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Unlock command sent to child: " + childId);
                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error sending unlock command to child", e);
                    if (callback != null) {
                        callback.onFailure(e.getMessage());
                    }
                });
    }
    
    /**
     * Get the latest command status for a child
     */
    public static void getCommandStatus(String childId, CommandStatusCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection("children")
                .document(childId)
                .collection("commands")
                .document("latest")
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String status = documentSnapshot.getString(FIELD_STATUS);
                        String type = documentSnapshot.getString(FIELD_TYPE);
                        String statusDetail = documentSnapshot.getString(FIELD_STATUS_DETAIL);
                        Long timestamp = documentSnapshot.getLong(FIELD_TIMESTAMP);
                        
                        if (callback != null) {
                            callback.onStatusReceived(status, type, statusDetail, timestamp);
                        }
                    } else if (callback != null) {
                        callback.onStatusReceived(null, null, null, null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting command status", e);
                    if (callback != null) {
                        callback.onFailure(e.getMessage());
                    }
                });
    }
    
    public interface CommandCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }
    
    public interface CommandStatusCallback {
        void onStatusReceived(String status, String commandType, String statusDetail, Long timestamp);
        void onFailure(String errorMessage);
    }
}
