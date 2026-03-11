package com.myapp.guidegrowth;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.myapp.guidegrowth.receivers.DeviceAdminReceiver;

public class ChildServiceManagerActivity extends AppCompatActivity {

    private static final int REQUEST_LOCATION_PERMISSION = 1001;
    private static final int REQUEST_BACKGROUND_LOCATION_PERMISSION = 1002;
    private static final int REQUEST_USAGE_ACCESS = 1003;
    private static final int REQUEST_DEVICE_ADMIN = 1004;
    private static final int REQUEST_OVERLAY_PERMISSION = 1005;
    private static final int REQUEST_NOTIFICATION_ACCESS = 1006;

    private MaterialCardView locationCard, usageAccessCard, deviceAdminCard, overlayCard, foregroundServiceCard;
    private Button allPermissionsBtn;
    private LinearLayout permissionsContainer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_manager);
        
        // Initialize UI elements
        locationCard = findViewById(R.id.locationPermissionCard);
        usageAccessCard = findViewById(R.id.usageAccessCard);
        deviceAdminCard = findViewById(R.id.deviceAdminCard);
        overlayCard = findViewById(R.id.overlayPermissionCard);
        foregroundServiceCard = findViewById(R.id.foregroundServiceCard);
        allPermissionsBtn = findViewById(R.id.requestAllPermissionsBtn);
        permissionsContainer = findViewById(R.id.permissionsContainer);
        
        // Set up permission request buttons
        setupPermissionButtons();
        
        // Setup all-in-one request button
        allPermissionsBtn.setOnClickListener(v -> requestAllPermissions());
        
        // Update permission cards status
        refreshPermissionCards();
    }
    
    private void setupPermissionButtons() {
        locationCard.setOnClickListener(v -> requestLocationPermissions());
        usageAccessCard.setOnClickListener(v -> requestUsageAccess());
        deviceAdminCard.setOnClickListener(v -> requestDeviceAdmin());
        overlayCard.setOnClickListener(v -> requestOverlayPermission());
        foregroundServiceCard.setOnClickListener(v -> checkNotificationPermission());
    }
    
    private void refreshPermissionCards() {
        updateLocationCardStatus();
        updateUsageAccessCardStatus();
        updateDeviceAdminCardStatus();
        updateOverlayCardStatus();
        updateForegroundServiceCardStatus();
    }
    
    private void requestAllPermissions() {
        requestLocationPermissions();
        // Other permissions will be requested sequentially after each result
    }
    
    // Location Permission (Foreground & Background)
    private void requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 
                    REQUEST_LOCATION_PERMISSION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Background location requires foreground first
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 
                        REQUEST_BACKGROUND_LOCATION_PERMISSION);
            } else {
                // Both permissions granted, proceed to next
                requestUsageAccess();
            }
        } else {
            // On older Android versions, proceed to next permission
            requestUsageAccess();
        }
    }
    
    private void updateLocationCardStatus() {
        boolean foregroundGranted = ContextCompat.checkSelfPermission(this, 
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                
        boolean backgroundGranted = true; // Default for older Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundGranted = ContextCompat.checkSelfPermission(this, 
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        
        locationCard.setStrokeColor(ContextCompat.getColor(this, 
                (foregroundGranted && backgroundGranted) ? R.color.permission_granted : R.color.permission_pending));
    }
    
    // Usage Access Permission
    private void requestUsageAccess() {
        if (!hasUsageAccessPermission()) {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivityForResult(intent, REQUEST_USAGE_ACCESS);
        } else {
            // Already has usage access, proceed to next
            requestDeviceAdmin();
        }
    }
    
    private boolean hasUsageAccessPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, 
                Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }
    
    private void updateUsageAccessCardStatus() {
        usageAccessCard.setStrokeColor(ContextCompat.getColor(this, 
                hasUsageAccessPermission() ? R.color.permission_granted : R.color.permission_pending));
    }
    
    // Device Admin Permission
    private void requestDeviceAdmin() {
        if (!isDeviceAdminActive()) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            ComponentName deviceAdminComponent = new ComponentName(this, DeviceAdminReceiver.class);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, 
                    "GuideGrowth needs device administrator access to enforce parental controls.");
            startActivityForResult(intent, REQUEST_DEVICE_ADMIN);
        } else {
            // Already has device admin, proceed to next
            requestOverlayPermission();
        }
    }
    
    private boolean isDeviceAdminActive() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName deviceAdminComponent = new ComponentName(this, DeviceAdminReceiver.class);
        return dpm.isAdminActive(deviceAdminComponent);
    }
    
    private void updateDeviceAdminCardStatus() {
        deviceAdminCard.setStrokeColor(ContextCompat.getColor(this, 
                isDeviceAdminActive() ? R.color.permission_granted : R.color.permission_pending));
    }
    
    // Draw Over Apps Permission
    private void requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        } else {
            // Already has overlay permission, proceed to next
            checkNotificationPermission();
        }
    }
    
    private void updateOverlayCardStatus() {
        overlayCard.setStrokeColor(ContextCompat.getColor(this, 
                Settings.canDrawOverlays(this) ? R.color.permission_granted : R.color.permission_pending));
    }
    
    // Foreground Service Permission (via Notification)
    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 
                        REQUEST_NOTIFICATION_ACCESS);
            } else {
                // Permission granted
                showPermissionsCompletedMessage();
            }
        } else {
            // Older versions don't need explicit notification permission
            showPermissionsCompletedMessage();
        }
    }
    
    private void updateForegroundServiceCardStatus() {
        boolean granted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted = ContextCompat.checkSelfPermission(this, 
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        foregroundServiceCard.setStrokeColor(ContextCompat.getColor(this, 
                granted ? R.color.permission_granted : R.color.permission_pending));
    }
    
    private void showPermissionsCompletedMessage() {
        // Check if all permissions are granted
        boolean allGranted = areAllPermissionsGranted();
        
        if (allGranted) {
            Snackbar.make(permissionsContainer, "All permissions granted successfully!", 
                    Snackbar.LENGTH_LONG).show();
            // Optionally start child monitoring service
            // startChildMonitoringService();
        } else {
            Snackbar.make(permissionsContainer, "Some permissions are still required.", 
                    Snackbar.LENGTH_LONG).show();
        }
    }
    
    private boolean areAllPermissionsGranted() {
        // Check all individual permissions
        boolean locationPermission = ContextCompat.checkSelfPermission(this, 
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                
        boolean backgroundLocation = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocation = ContextCompat.checkSelfPermission(this, 
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        
        boolean notificationPermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission = ContextCompat.checkSelfPermission(this, 
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        
        return locationPermission && backgroundLocation && 
               hasUsageAccessPermission() && isDeviceAdminActive() && 
               Settings.canDrawOverlays(this) && notificationPermission;
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Foreground location granted, request background if needed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    requestLocationPermissions(); // This will now request background
                } else {
                    requestUsageAccess(); // Move to next permission on older Android
                }
            } else {
                Toast.makeText(this, "Location permission is required for child monitoring", 
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_BACKGROUND_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestUsageAccess(); // Move to next permission
            } else {
                Toast.makeText(this, "Background location is needed for child safety features", 
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_NOTIFICATION_ACCESS) {
            showPermissionsCompletedMessage(); // Final permission check
        }
        
        // Refresh UI after permission changes
        refreshPermissionCards();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // Refresh all cards to reflect any permission changes
        refreshPermissionCards();
        
        if (requestCode == REQUEST_USAGE_ACCESS) {
            if (hasUsageAccessPermission()) {
                requestDeviceAdmin(); // Move to next permission
            } else {
                Toast.makeText(this, "Usage access is required to monitor screen time", 
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_DEVICE_ADMIN) {
            if (resultCode == Activity.RESULT_OK) {
                requestOverlayPermission(); // Move to next permission
            } else {
                Toast.makeText(this, "Device admin is required for parental controls", 
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                checkNotificationPermission(); // Move to next permission
            } else {
                Toast.makeText(this, "Overlay permission is needed to enforce restrictions", 
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
