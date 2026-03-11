package com.myapp.guidegrowth.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.myapp.guidegrowth.R;

/**
 * Helper class for UI related functionality with consistent minimal design
 */
public class UiHelper {
    
    // Standard colors
    private static final int COLOR_PRIMARY = Color.parseColor("#4285F4"); // Google blue
    private static final int COLOR_SUCCESS = Color.parseColor("#34A853"); // Google green
    private static final int COLOR_WARNING = Color.parseColor("#FBBC05"); // Google yellow
    private static final int COLOR_ERROR = Color.parseColor("#EA4335");   // Google red
    private static final int COLOR_NEUTRAL = Color.parseColor("#5F6368"); // Google grey
    
    /**
     * Apply minimal design to a button
     */
    public static void applyMinimalButtonDesign(Button button, boolean isPrimary) {
        if (button instanceof MaterialButton) {
            MaterialButton materialButton = (MaterialButton) button;
            
            if (isPrimary) {
                materialButton.setBackgroundTintList(ColorStateList.valueOf(COLOR_PRIMARY));
                materialButton.setTextColor(Color.WHITE);
                materialButton.setCornerRadius(8); // Slightly rounded corners
            } else {
                materialButton.setStrokeColor(ColorStateList.valueOf(COLOR_PRIMARY));
                materialButton.setStrokeWidth(2);
                materialButton.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
                materialButton.setTextColor(COLOR_PRIMARY);
            }
        } else {
            if (isPrimary) {
                button.setBackgroundColor(COLOR_PRIMARY);
                button.setTextColor(Color.WHITE);
            } else {
                button.setBackgroundColor(Color.TRANSPARENT);
                button.setTextColor(COLOR_PRIMARY);
            }
        }
    }
    
    /**
     * Apply minimal card design
     */
    public static void applyMinimalCardDesign(CardView cardView) {
        cardView.setCardElevation(4); // Minimal elevation
        cardView.setRadius(8);       // Slightly rounded corners
        cardView.setUseCompatPadding(true);
        
        if (cardView instanceof MaterialCardView) {
            MaterialCardView materialCard = (MaterialCardView) cardView;
            materialCard.setStrokeWidth(1);
            materialCard.setStrokeColor(Color.parseColor("#E0E0E0")); // Light border
        }
    }
    
    /**
     * Show success message with minimal design
     */
    public static void showSuccessMessage(View view, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT);
        View snackbarView = snackbar.getView();
        snackbarView.setBackgroundColor(COLOR_SUCCESS);
        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) {
            textView.setTextColor(Color.WHITE);
        }
        snackbar.show();
    }
    
    /**
     * Show error message with minimal design
     */
    public static void showErrorMessage(View view, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        View snackbarView = snackbar.getView();
        snackbarView.setBackgroundColor(COLOR_ERROR);
        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) {
            textView.setTextColor(Color.WHITE);
        }
        snackbar.show();
    }
    
    /**
     * Show warning message with minimal design
     */
    public static void showWarningMessage(View view, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        View snackbarView = snackbar.getView();
        snackbarView.setBackgroundColor(COLOR_WARNING);
        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) {
            textView.setTextColor(Color.BLACK); // Black text on yellow for better readability
        }
        snackbar.show();
    }
    
    /**
     * Set active status indicator on text
     */
    public static void setActiveStatusTextView(TextView textView, boolean isActive, Context context) {
        if (isActive) {
            textView.setTextColor(ContextCompat.getColor(context, R.color.service_active));
        } else {
            textView.setTextColor(ContextCompat.getColor(context, R.color.service_inactive));
        }
    }
}
