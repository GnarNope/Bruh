package com.myapp.guidegrowth.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.myapp.guidegrowth.R;
import com.myapp.guidegrowth.ui.child.ChildServiceManagerActivity;

/**
 * Utilities for managing notifications
 */
public class NotificationUtils {
    
    private static final String CHANNEL_ID = "guidegrowth_notifications";
    private static final String CHANNEL_NAME = "GuideGrowth Notifications";
    private static final String CHANNEL_DESC = "Notifications from GuideGrowth app";
    
    /**
     * Shows a notification to the user
     * 
     * @param context The context
     * @param title The notification title
     * @param message The notification message
     * @param priority The notification priority
     */
    public static void showNotification(Context context, String title, String message, int priority) {
        // Create notification channel for Android O and above
        createNotificationChannel(context);
        
        // Create an intent for when the notification is tapped
        Intent intent = new Intent(context, ChildServiceManagerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 
                PendingIntent.FLAG_IMMUTABLE);
        
        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(priority)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        
        // Show the notification
        NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
    
    private static void createNotificationChannel(Context context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(CHANNEL_DESC);
            
            // Register the channel with the system
            NotificationManager notificationManager = 
                    context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
