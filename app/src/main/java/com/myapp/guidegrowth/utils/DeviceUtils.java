package com.myapp.guidegrowth.utils;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import com.myapp.guidegrowth.receivers.DeviceAdminReceiver;

public class DeviceUtils {

    /**
     * Checks if the app has device admin privileges
     */
    public static boolean isDeviceAdminActive(Context context) {
        DevicePolicyManager devicePolicyManager = 
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName deviceAdmin = new ComponentName(context, DeviceAdminReceiver.class);
        return devicePolicyManager.isAdminActive(deviceAdmin);
    }

    /**
     * Lock the device screen
     */
    public static void lockDevice(Context context) {
        DevicePolicyManager devicePolicyManager = 
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName deviceAdmin = new ComponentName(context, DeviceAdminReceiver.class);
        
        if (devicePolicyManager.isAdminActive(deviceAdmin)) {
            devicePolicyManager.lockNow();
        }
    }

    /**
     * Open app settings
     */
    public static void openAppSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", context.getPackageName(), null);
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
