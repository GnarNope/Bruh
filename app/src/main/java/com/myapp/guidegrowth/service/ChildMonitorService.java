package com.myapp.guidegrowth.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.myapp.guidegrowth.R;
import com.myapp.guidegrowth.ui.child.BedtimeEnforcementActivity;
import com.myapp.guidegrowth.ui.child.ChildServiceManagerActivity;
import com.myapp.guidegrowth.ui.child.ScreenTimeLimitActivity;
import com.myapp.guidegrowth.utils.DeviceUtils;
import com.myapp.guidegrowth.utils.FirestoreWriteOptimizer;
import com.myapp.guidegrowth.utils.CommandUtils;
import com.myapp.guidegrowth.utils.NotificationUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ChildMonitorService extends Service {
    private static final String TAG = "ChildMonitorService";
    private static final String CHANNEL_ID = "ChildMonitorChannel";
    private static final int NOTIFICATION_ID = 1001;

    // Location tracking parameters - drastically increased to reduce writes
    private static final long LOCATION_UPDATE_INTERVAL = 30 * 60 * 1000; // 30 minutes
    private static final long LOCATION_FASTEST_INTERVAL = 15 * 60 * 1000; // 15 minutes

    // Usage stats parameters - increase interval to reduce write frequency
    private static final long USAGE_STATS_UPDATE_INTERVAL = 60 * 60 * 1000; // 60 minutes
    private static final long COMMAND_CHECK_INTERVAL = 60 * 1000; // 60 seconds

    private static final String PREF_PROCESSED_COMMANDS = "processed_commands";
    private final Set<String> processedCommandIds = Collections.synchronizedSet(new HashSet<>());
    private final Object commandLock = new Object();
    private SharedPreferences commandPrefs;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private FirebaseFirestore db;
    private FirebaseAuth firebaseAuth;
    private String childId;
    private FirestoreWriteOptimizer writeOptimizer;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ListenerRegistration commandsListener;

    // Add flag to track if location update was requested by parent
    private boolean isLocationRequestedByParent = false;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();
        writeOptimizer = FirestoreWriteOptimizer.getInstance(this);

        // Initialize shared preferences for command tracking
        commandPrefs = getSharedPreferences(PREF_PROCESSED_COMMANDS, Context.MODE_PRIVATE);

        // Load previously processed command IDs
        loadProcessedCommandIds();

        // Get current user ID (child ID)
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            childId = currentUser.getUid();
        } else {
            stopSelf();
            return;
        }

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Set up location callback
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        Log.d(TAG, "Location update: " + location.getLatitude() + ", " + location.getLongitude());
                        uploadLocationToFirestore(location);
                    }
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification());

        // Start location updates
        startLocationUpdates();

        // Start usage stats tracking
        startUsageStatsTracking();

        // Start command listener
        startCommandsListener();

        // Start limits enforcement
        startLimitsEnforcement();

        // Return sticky to ensure the service keeps running
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        // Clean up resources
        stopLocationUpdates();
        handler.removeCallbacksAndMessages(null);

        if (commandsListener != null) {
            commandsListener.remove();
        }

        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Child Monitor Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background monitoring for child safety");

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, ChildServiceManagerActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GuideGrowth is active")
                .setContentText("Monitoring child device activity")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    // Location Tracking Methods
    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted");
            return;
        }

        try {
            LocationRequest locationRequest;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationRequest = new LocationRequest.Builder(LOCATION_UPDATE_INTERVAL)
                    .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .build();
            } else {
                // For older Android versions
                locationRequest = new LocationRequest();
                locationRequest.setInterval(LOCATION_UPDATE_INTERVAL);
                locationRequest.setFastestInterval(LOCATION_FASTEST_INTERVAL);
                locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            }

            fusedLocationClient.requestLocationUpdates(locationRequest,
                    locationCallback, Looper.getMainLooper());

            // Get initial location
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    Log.d(TAG, "Initial location: " + location.getLatitude() + ", " + location.getLongitude());
                    uploadLocationToFirestore(location);
                } else {
                    Log.w(TAG, "Initial location is null");
                }
            });

            Log.i(TAG, "Location updates started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting location updates", e);
        }
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void uploadLocationToFirestore(Location location) {
        if (childId == null) {
            Log.e(TAG, "Child ID is null, can't upload location");
            return;
        }

        // Use the optimizer for location writes with parent request flag
        DocumentReference locationRef = db.collection("children").document(childId)
                .collection("location").document("current");

        writeOptimizer.optimizedLocationWrite(
                locationRef,
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy(),
                location.getProvider(),
                "location_" + childId,
                isLocationRequestedByParent  // Pass the parent request flag
        );

        // Reset the flag after upload
        if (isLocationRequestedByParent) {
            isLocationRequestedByParent = false;
        }
    }

    // Usage Stats Tracking Methods
    private void startUsageStatsTracking() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                collectAndUploadUsageStats();
                handler.postDelayed(this, USAGE_STATS_UPDATE_INTERVAL);
            }
        });
    }

    private void collectAndUploadUsageStats() {
        collectAndUploadUsageStats(false);
    }

    private void collectAndUploadUsageStats(boolean forceUpdate) {
        try {
            UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usageStatsManager == null) {
                Log.e(TAG, "UsageStatsManager is null");
                return;
            }

            // Get usage stats for the last 24 hours
            long endTime = System.currentTimeMillis();
            long startTime = endTime - TimeUnit.HOURS.toMillis(24);

            List<UsageStats> stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

            if (stats == null || stats.isEmpty()) {
                Log.w(TAG, "No usage stats data available. Check usage access permission.");
                return;
            }

            // Calculate total screen time
            long totalScreenTime = 0;
            Map<String, Object> appsData = new HashMap<>();

            // Don't limit to top 10 - include all apps with significant usage
            for (UsageStats usageStats : stats) {
                String packageName = usageStats.getPackageName();
                long timeInForeground = usageStats.getTotalTimeInForeground();

                // Only include apps that were used for more than 1 minute
                if (timeInForeground > 60000) { // 1 minute
                    totalScreenTime += timeInForeground;

                    Map<String, Object> appData = new HashMap<>();
                    appData.put("packageName", packageName);

                    try {
                        // Try to get the app name
                        PackageManager packageManager = getPackageManager();
                        String appName = packageManager.getApplicationLabel(
                                packageManager.getApplicationInfo(packageName, 0)).toString();
                        appData.put("appName", appName);
                    } catch (Exception e) {
                        appData.put("appName", packageName);
                    }

                    appData.put("totalTimeMs", timeInForeground);
                    appData.put("lastTimeUsed", usageStats.getLastTimeUsed());

                    appsData.put(packageName, appData);
                }
            }

            // Create a document with app usage data
            Map<String, Object> usageData = new HashMap<>();
            usageData.put("apps", appsData);
            usageData.put("totalScreenTimeMs", totalScreenTime);
            usageData.put("timestamp", System.currentTimeMillis());

            // Upload to Firestore using the optimizer
            if (childId != null) {
                DocumentReference usageStatsRef = db.collection("children").document(childId)
                        .collection("usageStats").document("latest");

                // Force update when requested by parent
                if (forceUpdate) {
                    usageStatsRef.set(usageData)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "App usage stats updated successfully (forced)");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error updating app usage stats: " + e.getMessage());
                        });
                } else {
                    writeOptimizer.optimizedWrite(
                            usageStatsRef,
                            usageData,
                            "usage_stats_" + childId,
                            false,
                            new FirestoreWriteOptimizer.WriteCallback() {
                                @Override
                                public void onWriteSuccess() {
                                    Log.d(TAG, "App usage stats updated successfully");
                                }

                                @Override
                                public void onWriteFailure(String errorMessage) {
                                    Log.e(TAG, "Error updating app usage stats: " + errorMessage);
                                }

                                @Override
                                public void onWriteSkipped(String reason) {
                                    Log.d(TAG, "App usage stats update skipped: " + reason);
                                }
                            });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error collecting usage stats", e);
        }
    }

    // Command Listener
    private void startCommandsListener() {
        if (childId == null) return;

        Log.d(TAG, "Starting command listener for child: " + childId);

        DocumentReference commandsRef = db.collection("children")
                .document(childId).collection("commands").document("latest");

        // First check if there are any pending commands
        commandsRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot != null && snapshot.exists()) {
                Map<String, Object> data = snapshot.getData();
                if (data != null) {
                    // Process any pending commands immediately, but only once
                    String commandId = generateCommandId(data);
                    if (!processedCommandIds.contains(commandId)) {
                        handleParentCommand(data);
                    } else {
                        Log.d(TAG, "Skipping already processed command: " + commandId);
                    }
                }
            }
        });

        // Set up real-time listener for new commands
        commandsListener = commandsRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Error listening for commands", error);
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                Map<String, Object> data = snapshot.getData();
                if (data != null) {
                    synchronized (commandLock) {
                        String commandId = generateCommandId(data);

                        // Only process if we haven't seen this command before
                        if (!processedCommandIds.contains(commandId)) {
                            // Mark command as received (even before processing)
                            CommandUtils.updateCommandStatus(childId, CommandUtils.STATUS_RECEIVED, null);

                            Log.d(TAG, "Received new command: " + commandId);
                            handleParentCommand(data);
                        }
                    }
                }
            }
        });
    }

    private void handleParentCommand(Map<String, Object> command) {
        String commandType = (String) command.get(CommandUtils.FIELD_TYPE);
        Long timestamp = (Long) command.get(CommandUtils.FIELD_TIMESTAMP);

        if (commandType == null) {
            Log.e(TAG, "Command type is null, ignoring command");
            CommandUtils.updateCommandStatus(childId, CommandUtils.STATUS_FAILED, "Invalid command format");
            return;
        }

        // Generate command ID for deduplication
        String commandId = generateCommandId(command);
        if (processedCommandIds.contains(commandId)) {
            Log.d(TAG, "Command already processed (duplicate), skipping: " + commandId);
            return;
        }

        Log.d(TAG, "Processing command: " + commandType + " timestamp: " + timestamp);

        // Skip old commands (if timestamp is more than 5 minutes old)
        long currentTime = System.currentTimeMillis();
        if (timestamp != null && (currentTime - timestamp > 5 * 60 * 1000)) {
            Log.d(TAG, "Skipping old command: " + commandType + " (timestamp: " + timestamp + ", current: " + currentTime + ")");
            CommandUtils.updateCommandStatus(childId, CommandUtils.STATUS_FAILED, "Command too old");

            // Add to processed commands to avoid reprocessing
            processedCommandIds.add(commandId);
            saveProcessedCommandIds();
            return;
        }

        // Define final variables for lambda use
        final boolean[] processed = {false};
        final String[] error = {null};

        try {
            if (CommandUtils.CMD_CHECK_IN.equals(commandType)) {
                // Always update location and usage stats when check-in command is received
                isLocationRequestedByParent = true;
                
                // Force update usage stats regardless of time threshold
                collectAndUploadUsageStats(true);
                
                // Get current location
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                        if (location != null) {
                            uploadLocationToFirestore(location);
                        }
                    });
                }
                
                processed[0] = true;

            } else if (CommandUtils.CMD_REQUEST_LOCATION.equals(commandType)) {
                // Set flag that parent requested location update
                isLocationRequestedByParent = true;
                requestImmediateUpdate();
                processed[0] = true;

            } else if (CommandUtils.CMD_LOCK_DEVICE.equals(commandType)) {
                if (DeviceUtils.isDeviceAdminActive(this)) {
                    DeviceUtils.lockDevice(this);
                    processed[0] = true;
                } else {
                    error[0] = "Device admin permission not granted";
                    Log.w(TAG, "Cannot lock device: " + error[0]);
                }

            } else if (CommandUtils.CMD_UNLOCK_DEVICE.equals(commandType)) {
                removeDeviceRestrictions();
                processed[0] = true;

            } else if (CommandUtils.CMD_SET_LIMITS.equals(commandType)) {
                updateAppLimits(command);
                processed[0] = true;

            } else if (CommandUtils.CMD_WARNING.equals(commandType)) {
                String message = (String) command.get(CommandUtils.FIELD_MESSAGE);
                if (message != null && !message.isEmpty()) {
                    showWarning(message);
                    processed[0] = true;
                } else {
                    error[0] = "Warning message is empty";
                }

            } else if (CommandUtils.CMD_OPEN_URL.equals(commandType)) {
                String url = (String) command.get(CommandUtils.FIELD_URL);
                if (url != null && !url.isEmpty()) {
                    openUrl(url);
                    processed[0] = true;
                } else {
                    error[0] = "URL is empty";
                }

            } else {
                error[0] = "Unknown command type: " + commandType;
                Log.w(TAG, error[0]);
            }
        } catch (Exception e) {
            error[0] = "Error processing command: " + e.getMessage();
            Log.e(TAG, error[0], e);
        }

        // Record command as processed (to avoid reprocessing)
        processedCommandIds.add(commandId);
        saveProcessedCommandIds();

        // Get the final values for the lambda
        final boolean finalProcessed = processed[0];
        final String finalError = error[0];

        // Update command status based on processing result
        Map<String, Object> statusUpdate = new HashMap<>();
        statusUpdate.put(CommandUtils.FIELD_STATUS, 
            finalProcessed ? CommandUtils.STATUS_PROCESSED : CommandUtils.STATUS_FAILED);
        statusUpdate.put("processedAt", System.currentTimeMillis());

        if (finalError != null) {
            statusUpdate.put(CommandUtils.FIELD_STATUS_DETAIL, finalError);
            statusUpdate.put(CommandUtils.FIELD_ERROR, finalError);
        }

        // Use a direct update to just modify the status fields
        db.collection("children")
            .document(childId)
            .collection("commands")
            .document("latest")
            .update(statusUpdate)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Command status updated: " + 
                (finalProcessed ? CommandUtils.STATUS_PROCESSED : CommandUtils.STATUS_FAILED)))
            .addOnFailureListener(e -> Log.e(TAG, "Error updating command status", e));
    }

    private void requestImmediateUpdate() {
        // Get current location
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    uploadLocationToFirestore(location);
                }
            });
        }

        // Get current usage stats - force an update regardless of time thresholds
        collectAndUploadUsageStats(true);
    }

    private void updateAppLimits(Map<String, Object> command) {
        // Store limits locally and in shared preferences for persistence
        Map<String, Object> limits = (Map<String, Object>) command.get("limits");
        if (limits == null) return;

        SharedPreferences prefs = getSharedPreferences("app_limits", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // Store daily screen time limit
        if (limits.containsKey("dailyScreenTimeHours")) {
            int hours = ((Number) limits.get("dailyScreenTimeHours")).intValue();
            editor.putInt("daily_screen_time_hours", hours);
        }

        // Store bedtime
        if (limits.containsKey("bedtimeStart")) {
            editor.putString("bedtime_start", (String) limits.get("bedtimeStart"));
        }

        if (limits.containsKey("bedtimeEnd")) {
            editor.putString("bedtime_end", (String) limits.get("bedtimeEnd"));
        }

        editor.apply();
        Log.d(TAG, "App limits updated");

        // Start enforcing limits immediately
        enforceAppLimits();
    }

    private void startLimitsEnforcement() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                enforceAppLimits();
                handler.postDelayed(this, 60 * 1000); // Check every minute
            }
        }, 60 * 1000);
    }

    private void enforceAppLimits() {
        try {
            // Check if we should enforce limits (have needed permissions)
            if (!Settings.canDrawOverlays(this)) {
                Log.w(TAG, "Cannot enforce limits: overlay permission not granted");
                return;
            }

            SharedPreferences prefs = getSharedPreferences("app_limits", MODE_PRIVATE);

            // Check bedtime
            String bedtimeStart = prefs.getString("bedtime_start", "");
            String bedtimeEnd = prefs.getString("bedtime_end", "");

            if (!bedtimeStart.isEmpty() && !bedtimeEnd.isEmpty()) {
                if (isCurrentTimeBetween(bedtimeStart, bedtimeEnd)) {
                    showBedtimeOverlay();
                    return;
                }
            }

            // Check daily screen time
            int dailyScreenTimeHours = prefs.getInt("daily_screen_time_hours", 0);
            if (dailyScreenTimeHours > 0) {
                long screenTimeLimitMs = dailyScreenTimeHours * 60 * 60 * 1000;
                long currentScreenTimeMs = getTodayScreenTime();

                Log.d(TAG, "Screen time limit: " + (screenTimeLimitMs/60000) + " minutes, Current usage: " + 
                    (currentScreenTimeMs/60000) + " minutes");
                    
                if (currentScreenTimeMs > screenTimeLimitMs) {
                    Log.i(TAG, "Screen time limit reached: showing limit overlay");
                    showScreenTimeLimitOverlay();
                    return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enforcing app limits", e);
        }
    }

    private boolean isCurrentTimeBetween(String startTime, String endTime) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date currentTime = new Date();
            Date start = sdf.parse(startTime);
            Date end = sdf.parse(endTime);

            // Create calendar instances for comparison
            Calendar currentCal = Calendar.getInstance();

            Calendar startCal = Calendar.getInstance();
            startCal.setTime(start);
            startCal.set(Calendar.YEAR, currentCal.get(Calendar.YEAR));
            startCal.set(Calendar.MONTH, currentCal.get(Calendar.MONTH));
            startCal.set(Calendar.DAY_OF_MONTH, currentCal.get(Calendar.DAY_OF_MONTH));

            Calendar endCal = Calendar.getInstance();
            endCal.setTime(end);
            endCal.set(Calendar.YEAR, currentCal.get(Calendar.YEAR));
            endCal.set(Calendar.MONTH, currentCal.get(Calendar.MONTH));
            endCal.set(Calendar.DAY_OF_MONTH, currentCal.get(Calendar.DAY_OF_MONTH));

            // Handle overnight bedtime (e.g., 22:00 - 07:00)
            if (endCal.before(startCal)) {
                endCal.add(Calendar.DAY_OF_MONTH, 1);
                if (currentCal.before(startCal)) {
                    currentCal.add(Calendar.DAY_OF_MONTH, 1);
                }
            }

            return currentCal.after(startCal) && currentCal.before(endCal);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing bedtime", e);
            return false;
        }
    }

    private long getTodayScreenTime() {
        try {
            UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usageStatsManager == null) return 0;

            // Set the start time to beginning of today (midnight)
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long startTime = calendar.getTimeInMillis();
            
            // End time is current time
            long endTime = System.currentTimeMillis();

            Log.d(TAG, "Getting screen time from " + new Date(startTime) + " to " + new Date(endTime));

            // Make sure we're using INTERVAL_DAILY for the current day only
            List<UsageStats> stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

            // Sum up all app usage for today only
            long totalScreenTime = 0;
            if (stats != null) {
                for (UsageStats usageStats : stats) {
                    // Only count time in foreground
                    long timeInForeground = usageStats.getTotalTimeInForeground();
                    String packageName = usageStats.getPackageName();
                    
                    // Skip system UI and very short usage periods
                    if (timeInForeground > 1000 && 
                        !packageName.equals("android") && 
                        !packageName.equals("com.android.systemui") &&
                        !packageName.equals("com.myapp.guidegrowth")) { // Skip our own app
                        
                        // Add this app's usage time to total
                        totalScreenTime += timeInForeground;
                        
                        // Log each app's usage for debugging
                        if (timeInForeground > 60000) { // Only log apps with >1min usage
                            Log.d(TAG, "App: " + packageName + ", Today's usage: " 
                                + (timeInForeground/60000) + " minutes");
                        }
                    }
                }
            }

            Log.d(TAG, "Total screen time for today: " + (totalScreenTime/60000) + " minutes");
            
            // Return the total screen time in milliseconds
            return totalScreenTime;
        } catch (Exception e) {
            Log.e(TAG, "Error getting today's screen time", e);
            return 0;
        }
    }

    // Show overlays for enforcement
    private void showBedtimeOverlay() {
        Intent intent = new Intent(this, BedtimeEnforcementActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void showScreenTimeLimitOverlay() {
        Intent intent = new Intent(this, ScreenTimeLimitActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void removeDeviceRestrictions() {
        Log.d(TAG, "Removing device restrictions");

        // Clear any active screen time limits or app restrictions
        SharedPreferences prefs = getSharedPreferences("app_restrictions", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("restrictions_active", false);
        editor.apply();

        // Notify user that restrictions have been removed
        NotificationUtils.showNotification(
                this,
                "Restrictions Removed",
                "Your parent has removed app restrictions",
                NotificationCompat.PRIORITY_HIGH);

        // Broadcast intent for any active enforcement activities to finish
        Intent intent = new Intent("com.myapp.guidegrowth.RESTRICTIONS_REMOVED");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void showWarning(String message) {
        // Show a toast message
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());

        // For more persistent warnings, you could show a dialog or a custom overlay
        // but that would require additional setup
    }

    private void openUrl(String url) {
        // Create intent with CATEGORY_BROWSABLE to respect default browser settings
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setData(Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        try {
            startActivity(intent);
            Log.d(TAG, "Opening URL: " + url);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "No app found to handle URL: " + url, e);
            
            // Show a notification since we couldn't open the URL
            NotificationUtils.showNotification(
                    this,
                    "Cannot Open Link",
                    "No app found to open the link: " + url,
                    NotificationCompat.PRIORITY_HIGH);
        }
    }

    private String generateCommandId(Map<String, Object> commandData) {
        StringBuilder idBuilder = new StringBuilder();

        // Include command type in ID
        String commandType = (String) commandData.get(CommandUtils.FIELD_TYPE);
        if (commandType != null) {
            idBuilder.append(commandType).append("_");
        }

        // Include timestamp in ID
        Long timestamp = (Long) commandData.get(CommandUtils.FIELD_TIMESTAMP);
        if (timestamp != null) {
            idBuilder.append(timestamp);
        } else {
            // If no timestamp, use current time
            idBuilder.append(System.currentTimeMillis());
        }

        // Include other relevant fields if needed
        String message = (String) commandData.get(CommandUtils.FIELD_MESSAGE);
        if (message != null && !message.isEmpty()) {
            idBuilder.append("_").append(message.hashCode());
        }

        // Return the generated ID
        return idBuilder.toString();
    }

    private void loadProcessedCommandIds() {
        try {
            Set<String> savedIds = commandPrefs.getStringSet("command_ids", new HashSet<>());
            if (savedIds != null) {
                processedCommandIds.addAll(savedIds);

                // Remove older entries if there are too many (keep most recent 50)
                if (processedCommandIds.size() > 50) {
                    List<String> idsList = new ArrayList<>(processedCommandIds);
                    Collections.sort(idsList); // Assuming IDs contain timestamps for sorting

                    // Remove oldest entries
                    Set<String> toRemove = new HashSet<>();
                    for (int i = 0; i < idsList.size() - 50; i++) {
                        toRemove.add(idsList.get(i));
                    }

                    processedCommandIds.removeAll(toRemove);

                    // Save the cleaned list
                    commandPrefs.edit()
                        .putStringSet("command_ids", new HashSet<>(processedCommandIds))
                        .apply();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading processed command IDs", e);
        }
    }

    private void saveProcessedCommandIds() {
        commandPrefs.edit()
            .putStringSet("command_ids", new HashSet<>(processedCommandIds))
            .apply();
    }
}
