package com.myapp.guidegrowth.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.myapp.guidegrowth.databinding.ActivityRoleSelectionBinding;
import com.myapp.guidegrowth.model.User;
import com.myapp.guidegrowth.ui.child.ChildServiceManagerActivity;
import com.myapp.guidegrowth.ui.parent.ParentDashboardActivity;
import com.myapp.guidegrowth.utils.FirestoreWriteOptimizer;
import com.myapp.guidegrowth.viewmodel.AuthViewModel;
import com.myapp.guidegrowth.viewmodel.UserViewModel;

import java.util.HashMap;
import java.util.Map;

public class RoleSelectionActivity extends AppCompatActivity {
    
    private ActivityRoleSelectionBinding binding;
    private AuthViewModel authViewModel;
    private UserViewModel userViewModel;
    private FirestoreWriteOptimizer writeOptimizer;
    private boolean isNewUser = false;
    private String selectedRole = "";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRoleSelectionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        writeOptimizer = FirestoreWriteOptimizer.getInstance(this);
        
        // Show loading indicator
        binding.progressBar.setVisibility(View.VISIBLE);
        
        // Always check if the user already has a role and linked ID
        // to avoid unnecessary role selection
        authViewModel.checkUserRoleAndLinking(new AuthViewModel.UserRoleCallback() {
            @Override
            public void onResult(boolean hasRole, String role, boolean isLinked) {
                binding.progressBar.setVisibility(View.GONE);
                
                if (hasRole && isLinked) {
                    // User already has both role and linked ID, navigate directly
                    navigateBasedOnRole(role);
                } else if (hasRole && !isLinked) {
                    // User has role but no linked ID, show connection screen
                    selectedRole = role;
                    showParentChildIdField();
                } else {
                    // No role set, check if we have user data already
                    checkUserData();
                }
            }
        });
        
        setupClickListeners();
        observeViewModel();
    }
    
    private void checkUserData() {
        authViewModel.getCurrentUser().observe(this, user -> {
            if (user != null) {
                // If we already have user role set, redirect based on role
                String role = user.getUserType(); // Keep using getUserType for backward compatibility
                if (role != null && !role.isEmpty()) {
                    navigateBasedOnRole(role);
                } else {
                    // This is a new user who needs to set up their profile
                    isNewUser = true;
                    binding.nameInputLayout.setVisibility(View.VISIBLE);
                }
            }
        });
    }
    
    private void setupClickListeners() {
        binding.parentRoleButton.setOnClickListener(v -> {
            selectedRole = "parent";
            showParentChildIdField();
        });
        
        binding.childRoleButton.setOnClickListener(v -> {
            selectedRole = "child";
            showParentChildIdField();
        });
        
        binding.buttonContinue.setOnClickListener(v -> {
            if (validateInputs()) {
                setUserRole(selectedRole);
            }
        });
    }
    
    private void showParentChildIdField() {
        binding.roleSelectionLayout.setVisibility(View.GONE);
        binding.connectionLayout.setVisibility(View.VISIBLE);
        
        if (selectedRole.equals("parent")) {
            binding.connectionTitleText.setText("Link to Child Account");
            binding.connectionSubtitleText.setText("Enter your child's User ID to connect your accounts");
            binding.connectionIdInputLayout.setHint("Child's User ID");
            binding.connectionIdOptionalText.setVisibility(View.VISIBLE);
        } else {
            binding.connectionTitleText.setText("Link to Parent Account");
            binding.connectionSubtitleText.setText("Enter your parent's User ID to connect your accounts");
            binding.connectionIdInputLayout.setHint("Parent's User ID");
            binding.connectionIdOptionalText.setVisibility(View.GONE);
        }
    }
    
    private boolean validateInputs() {
        boolean isValid = true;
        String name = binding.nameInput.getText().toString().trim();
        String connectionId = binding.connectionIdInput.getText().toString().trim();
        
        // Name validation (required for new users)
        if (isNewUser && TextUtils.isEmpty(name)) {
            binding.nameInputLayout.setError("Please enter your name");
            isValid = false;
        } else {
            binding.nameInputLayout.setError(null);
        }
        
        // For child accounts, parent ID is required
        if (selectedRole.equals("child") && TextUtils.isEmpty(connectionId)) {
            binding.connectionIdInputLayout.setError("Please enter your parent's User ID");
            isValid = false;
        } else {
            binding.connectionIdInputLayout.setError(null);
        }
        
        return isValid;
    }
    
    private void setUserRole(String role) {
        String name = binding.nameInput.getText().toString().trim();
        String connectionId = binding.connectionIdInput.getText().toString().trim();
        
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.buttonContinue.setEnabled(false);
        
        if (role.equals("parent")) {
            // Parent role - child ID is optional
            userViewModel.updateUserProfileWithConnection(name, role, connectionId, true);
        } else {
            // Child role - parent ID is required
            userViewModel.updateUserProfileWithConnection(name, role, connectionId, false);
            
            // For child accounts, create a document in the children collection as well
            if (!TextUtils.isEmpty(name)) {
                createChildDocumentInFirestore(name);
            }
        }
    }
    
    private void createChildDocumentInFirestore(String name) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        
        String childId = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String parentId = binding.connectionIdInput.getText().toString().trim();
        
        // Update user document with proper role
        Map<String, Object> userData = new HashMap<>();
        userData.put("role", "child");
        if (!TextUtils.isEmpty(parentId)) {
            userData.put("linkedUserId", parentId);
        }
        
        DocumentReference childRef = db.collection("users").document(childId);
        writeOptimizer.optimizedWrite(
                childRef, 
                userData, 
                "role_selection_child_" + childId,
                true, // Force this important write
                null // No callback needed
        );
        
        // Create child document in children collection
        Map<String, Object> childData = new HashMap<>();
        childData.put("name", name);
        childData.put("email", currentUser.getEmail());
        childData.put("lastUpdated", System.currentTimeMillis());
        
        DocumentReference childDocRef = db.collection("children").document(childId);
        writeOptimizer.optimizedWrite(
                childDocRef,
                childData,
                "child_document_" + childId,
                true, // Force this important write
                new FirestoreWriteOptimizer.WriteCallback() {
                    @Override
                    public void onWriteSuccess() {
                        // Success silently
                    }

                    @Override
                    public void onWriteFailure(String errorMessage) {
                        Toast.makeText(RoleSelectionActivity.this,
                                "Warning: Could not create child profile completely",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onWriteSkipped(String reason) {
                        // Should not happen with force=true
                    }
                });
    }
    
    private void observeViewModel() {
        userViewModel.getProfileUpdateResult().observe(this, success -> {
            binding.progressBar.setVisibility(View.GONE);
            
            if (success) {
                // Navigate based on the selected role
                navigateBasedOnRole(selectedRole);
            } else {
                binding.buttonContinue.setEnabled(true);
                Toast.makeText(this, "Failed to update profile. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
        
        userViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                binding.progressBar.setVisibility(View.GONE);
                binding.buttonContinue.setEnabled(true);
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
}
