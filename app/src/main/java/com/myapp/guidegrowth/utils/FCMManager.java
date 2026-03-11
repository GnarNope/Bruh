package com.myapp.guidegrowth.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

/**
 * Utility class to manage Firebase Cloud Messaging
 */
public class FCMManager {
    private static final String TAG = "FCMManager";
    private static FCMManager instance;
    
    private FCMManager() {
    }
    
    public static synchronized FCMManager getInstance() {
        if (instance == null) {
            instance = new FCMManager();
        }
        return instance;
    }
    
    public void getToken(OnCompleteListener<String> listener) {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(listener);
    }
    
    public void subscribeToTopic(String topic, OnCompleteListener<Void> listener) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
                .addOnCompleteListener(listener);
    }
    
    public void unsubscribeFromTopic(String topic, OnCompleteListener<Void> listener) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                .addOnCompleteListener(listener);
    }
    
    public void deleteToken(OnCompleteListener<Void> listener) {
        FirebaseMessaging.getInstance().deleteToken()
                .addOnCompleteListener(listener);
    }
}
