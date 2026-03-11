package com.myapp.guidegrowth.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.firebase.firestore.FirebaseFirestore;
import com.myapp.guidegrowth.ChildServiceManagerActivity;
import com.myapp.guidegrowth.databinding.ActivityLoginBinding;
import com.myapp.guidegrowth.ui.parent.ParentDashboardActivity;
import com.myapp.guidegrowth.viewmodel.AuthViewModel;

public class LoginActivity extends AppCompatActivity {
    
    private ActivityLoginBinding binding;
    private AuthViewModel authViewModel;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Initialize ViewModel
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        
        // Check if user is already logged in
        if (authViewModel.isUserLoggedIn()) {
            // If logged in, check role and navigate appropriately
            checkUserRoleAndNavigate();
            return;
        }
        
        setupClickListeners();
        observeViewModel();
    }
    
    private void setupClickListeners() {
        // Login Button
        binding.buttonLogin.setOnClickListener(v -> {
            if (validateInputs()) {
                loginUser();
            }
        });
        
        // Register Button
        binding.buttonRegister.setOnClickListener(v -> {
            if (validateInputs()) {
                registerUser();
            }
        });
        
        // Forgot Password Text
        binding.textForgotPassword.setOnClickListener(v -> {
            // Navigate to Forgot Password screen or show dialog
            String email = binding.inputEmail.getText().toString().trim();
            if (!TextUtils.isEmpty(email)) {
                // TODO: Implement password reset functionality
                Toast.makeText(LoginActivity.this, "Password reset link sent to your email", Toast.LENGTH_SHORT).show();
            } else {
                binding.inputEmailLayout.setError("Enter email to reset password");
            }
        });
    }
    
    private boolean validateInputs() {
        boolean isValid = true;
        String email = binding.inputEmail.getText().toString().trim();
        String password = binding.inputPassword.getText().toString().trim();
        
        if (TextUtils.isEmpty(email)) {
            binding.inputEmailLayout.setError("Email is required");
            isValid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.inputEmailLayout.setError("Enter a valid email");
            isValid = false;
        } else {
            binding.inputEmailLayout.setError(null);
        }
        
        if (TextUtils.isEmpty(password)) {
            binding.inputPasswordLayout.setError("Password is required");
            isValid = false;
        } else if (password.length() < 6) {
            binding.inputPasswordLayout.setError("Password must be at least 6 characters");
            isValid = false;
        } else {
            binding.inputPasswordLayout.setError(null);
        }
        
        return isValid;
    }
    
    private void loginUser() {
        String email = binding.inputEmail.getText().toString().trim();
        String password = binding.inputPassword.getText().toString().trim();
        
        authViewModel.loginUser(email, password);
    }
    
    private void registerUser() {
        String email = binding.inputEmail.getText().toString().trim();
        String password = binding.inputPassword.getText().toString().trim();
        
        // For registration, we'll collect name and user type in the RoleSelectionActivity
        // Create a temporary user account for now
        authViewModel.registerUser(email, password, "", "");
    }
    
    private void observeViewModel() {
        // Observe loading state
        authViewModel.getIsLoading().observe(this, isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.buttonLogin.setEnabled(!isLoading);
            binding.buttonRegister.setEnabled(!isLoading);
        });
        
        // Observe login success
        authViewModel.getLoginSuccess().observe(this, success -> {
            if (success) {
                // Just call handleLoginSuccess without trying to get the User object
                handleLoginSuccess();
            }
        });
        
        // Observe registration success
        authViewModel.getRegistrationSuccess().observe(this, success -> {
            if (success) {
                navigateToRoleSelection();
                finish();
            }
        });
        
        // Observe error messages
        authViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(LoginActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    /**
     * Handle successful login by checking user role and navigating appropriately
     */
    private void handleLoginSuccess() {
        binding.progressBar.setVisibility(View.GONE);
        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show();
        
        // Check role and navigate appropriately
        checkUserRoleAndNavigate();
    }
    
    /**
     * Checks the current user's role and navigates to the appropriate screen
     */
    private void checkUserRoleAndNavigate() {
        binding.progressBar.setVisibility(View.VISIBLE);
        
        authViewModel.checkUserRoleAndLinking(new AuthViewModel.UserRoleCallback() {
            @Override
            public void onResult(boolean hasRole, String role, boolean isLinked) {
                binding.progressBar.setVisibility(View.GONE);
                
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
    
    private void navigateToRoleSelection() {
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
