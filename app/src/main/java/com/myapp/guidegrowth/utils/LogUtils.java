package com.myapp.guidegrowth.utils;

import android.util.Log;

/**
 * Utility class for consistent logging across the app
 */
public class LogUtils {
    private static final boolean DEBUG = true;
    private static final String APP_TAG = "GuideGrowth";
    
    public static void debug(String tag, String message) {
        if (DEBUG) {
            Log.d(APP_TAG + "-" + tag, message);
        }
    }
    
    public static void info(String tag, String message) {
        Log.i(APP_TAG + "-" + tag, message);
    }
    
    public static void warn(String tag, String message) {
        Log.w(APP_TAG + "-" + tag, message);
    }
    
    public static void error(String tag, String message) {
        Log.e(APP_TAG + "-" + tag, message);
    }
    
    public static void error(String tag, String message, Throwable e) {
        Log.e(APP_TAG + "-" + tag, message, e);
    }
}
