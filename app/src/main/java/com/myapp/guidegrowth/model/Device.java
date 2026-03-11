package com.myapp.guidegrowth.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Model class for Device data (typically a child's device)
 */
public class Device {
    private String id;
    private String userId; // The child user ID
    private String deviceName;
    private String deviceModel;
    private String fcmToken;
    private String status; // "online", "offline", "restricted", etc.
    private long lastSeen;
    private Map<String, Object> restrictions;
    private Map<String, Object> usageStats;
    private String parentId; // Parent device identifier (for child devices)
    private String childId;  // Child device identifier (for parent devices)
    
    // Empty constructor required for Firestore
    public Device() {
        restrictions = new HashMap<>();
        usageStats = new HashMap<>();
    }
    
    public Device(String id, String userId, String deviceName, String deviceModel) {
        this.id = id;
        this.userId = userId;
        this.deviceName = deviceName;
        this.deviceModel = deviceModel;
        this.status = "online";
        this.lastSeen = System.currentTimeMillis();
        this.restrictions = new HashMap<>();
        this.usageStats = new HashMap<>();
    }
    
    /**
     * Add or update a restriction to the device
     * @param key The restriction key (e.g., "screenTime", "appBlocking")
     * @param value The restriction value
     */
    public void addRestriction(String key, Object value) {
        if (restrictions == null) {
            restrictions = new HashMap<>();
        }
        restrictions.put(key, value);
    }
    
    /**
     * Remove a restriction from the device
     * @param key The restriction key to remove
     */
    public void removeRestriction(String key) {
        if (restrictions != null) {
            restrictions.remove(key);
        }
    }
    
    /**
     * Check if a specific restriction exists
     * @param key The restriction key to check
     * @return true if the restriction exists, false otherwise
     */
    public boolean hasRestriction(String key) {
        return restrictions != null && restrictions.containsKey(key);
    }
    
    /**
     * Get a restriction value
     * @param key The restriction key
     * @return The restriction value or null if not found
     */
    public Object getRestriction(String key) {
        if (restrictions != null) {
            return restrictions.get(key);
        }
        return null;
    }
    
    /**
     * Link this device to a parent device
     * @param parentId The parent device ID
     */
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
    
    /**
     * Get the parent device ID
     * @return The parent device ID or null if not set
     */
    public String getParentId() {
        return parentId;
    }
    
    /**
     * Link this device to a child device
     * @param childId The child device ID
     */
    public void setChildId(String childId) {
        this.childId = childId;
    }
    
    /**
     * Get the child device ID
     * @return The child device ID or null if not set
     */
    public String getChildId() {
        return childId;
    }
    
    /**
     * Convert the device object to a map for Firestore storage
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("userId", userId);
        map.put("deviceName", deviceName);
        map.put("deviceModel", deviceModel);
        map.put("status", status);
        map.put("lastSeen", lastSeen);
        
        if (fcmToken != null) {
            map.put("fcmToken", fcmToken);
        }
        
        if (restrictions != null && !restrictions.isEmpty()) {
            map.put("restrictions", restrictions);
        }
        
        if (usageStats != null && !usageStats.isEmpty()) {
            map.put("usageStats", usageStats);
        }
        
        // Include parent-child relationship data
        if (parentId != null) {
            map.put("parentId", parentId);
        }
        
        if (childId != null) {
            map.put("childId", childId);
        }
        
        return map;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public Map<String, Object> getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(Map<String, Object> restrictions) {
        this.restrictions = restrictions;
    }

    public Map<String, Object> getUsageStats() {
        return usageStats;
    }

    public void setUsageStats(Map<String, Object> usageStats) {
        this.usageStats = usageStats;
    }
}
