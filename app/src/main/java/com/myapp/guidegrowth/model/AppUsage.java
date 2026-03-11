package com.myapp.guidegrowth.model;

public class AppUsage {
    private String packageName;
    private String appName;
    private long usageTimeMs;
    private long lastUsed;
    
    public AppUsage(String packageName, String appName, long usageTimeMs, long lastUsed) {
        this.packageName = packageName;
        this.appName = appName;
        this.usageTimeMs = usageTimeMs;
        this.lastUsed = lastUsed;
    }
    
    public String getPackageName() {
        return packageName;
    }
    
    public String getAppName() {
        return appName;
    }
    
    public long getUsageTimeMs() {
        return usageTimeMs;
    }
    
    public long getLastUsed() {
        return lastUsed;
    }
    
    public String getFormattedUsageTime() {
        long seconds = usageTimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }
}
