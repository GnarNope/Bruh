package com.myapp.guidegrowth.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model class for User data
 */
public class User {
    private String id;
    private String email;
    private String name;
    private String userType; // "parent" or "child"
    private String parentId; // Only for child accounts, reference to parent user
    private List<String> childrenIds; // Only for parent accounts, references to child users
    private String fcmToken;
    private Map<String, Object> settings;
    private long createdAt;
    private long lastLoginAt;
    private String linkedUserId; // For backward compatibility

    // Empty constructor required for Firestore
    public User() {
        childrenIds = new ArrayList<>();
        settings = new HashMap<>();
    }

    public User(String id, String email, String name, String userType, long createdAt) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.userType = userType;
        this.createdAt = createdAt;
        this.lastLoginAt = System.currentTimeMillis();
        this.childrenIds = new ArrayList<>();
        this.settings = new HashMap<>();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("email", email);
        map.put("name", name);
        map.put("userType", userType);
        map.put("createdAt", createdAt);
        map.put("lastLoginAt", lastLoginAt);

        if (userType.equals("parent")) {
            map.put("childrenIds", childrenIds);
        } else if (userType.equals("child")) {
            map.put("parentId", parentId);
        }

        if (fcmToken != null) {
            map.put("fcmToken", fcmToken);
        }

        if (settings != null && !settings.isEmpty()) {
            map.put("settings", settings);
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public List<String> getChildrenIds() {
        return childrenIds;
    }

    public void setChildrenIds(List<String> childrenIds) {
        this.childrenIds = childrenIds;
    }

    public void addChildId(String childId) {
        if (this.childrenIds == null) {
            this.childrenIds = new ArrayList<>();
        }
        this.childrenIds.add(childId);
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, Object> settings) {
        this.settings = settings;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(long lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    /**
     * Get the linked user ID (for backward compatibility)
     * @return The linked user ID or null if not set
     */
    public String getLinkedUserId() {
        return linkedUserId;
    }

    /**
     * Set the linked user ID (for backward compatibility)
     * @param linkedUserId The linked user ID
     */
    public void setLinkedUserId(String linkedUserId) {
        this.linkedUserId = linkedUserId;
    }
}
