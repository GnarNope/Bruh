package com.myapp.guidegrowth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.myapp.guidegrowth.databinding.ActivityMainBinding;
import com.myapp.guidegrowth.ui.auth.LoginActivity;
import com.myapp.guidegrowth.ui.auth.RoleSelectionActivity;
import com.myapp.guidegrowth.ui.child.ChildServiceManagerActivity;
import com.myapp.guidegrowth.ui.parent.ParentDashboardActivity;
import com.myapp.guidegrowth.viewmodel.AuthViewModel;

/**
 * Main entry point for the application.
 * Serves as a splash screen and handles automatic role detection.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private AuthViewModel authViewModel;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        
        // Show progress indicator
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.appLogo.setVisibility(View.VISIBLE);
        binding.appTitle.setVisibility(View.VISIBLE);
        
        // Check if user is logged in and determine where to navigate
        if (authViewModel.isUserLoggedIn()) {
            checkUserRoleAndNavigate();
        } else {
            // Short delay for splash screen effect, then navigate to login
            new Handler(Looper.getMainLooper()).postDelayed(this::navigateToLogin, 1500);
        }
    }
    
    /**
     * Checks the current user's role and navigates to the appropriate screen
     */
    private void checkUserRoleAndNavigate() {
        authViewModel.checkUserRoleAndLinking(new AuthViewModel.UserRoleCallback() {
            @Override
            public void onResult(boolean hasRole, String role, boolean isLinked) {
                if (hasRole && isLinked) {
                    // User has both role and linked ID, navigate directly
                    navigateBasedOnRole(role);
                } else {
                    // User is missing role or linked ID, go to role selection
                    navigateToRoleSelection();
                }
            }
        });
    }
    
    /**
     * Navigates to the appropriate screen based on user role
     */
    private void navigateBasedOnRole(String role) {
        Intent intent;
        
        if ("parent".equals(role)) {
            intent = new Intent(this, ParentDashboardActivity.class);
        } else {
            intent = new Intent(this, ChildServiceManagerActivity.class);
        }
        
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    /**
     * Navigates to the login screen
     */
    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    /**
     * Navigates to the role selection screen
     */
    private void navigateToRoleSelection() {
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}