package com.myapp.guidegrowth.ui.parent;

import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import com.myapp.guidegrowth.R;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.myapp.guidegrowth.databinding.ActivityAddChildBinding;
import com.myapp.guidegrowth.viewmodel.UserViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddChildActivity extends AppCompatActivity {

    private ActivityAddChildBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String parentId;
    private UserViewModel userViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddChildBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Add Child");
        }

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);

        // Get current user ID (parent ID)
        if (auth.getCurrentUser() != null) {
            parentId = auth.getCurrentUser().getUid();
        } else {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupListeners();
    }

    private void setupListeners() {
        binding.linkChildButton.setOnClickListener(v -> {
            String childId = binding.childIdInput.getText().toString().trim();
            if (validateChildId(childId)) {
                linkChildAccount(childId);
            }
        });
    }

    private boolean validateChildId(String childId) {
        if (TextUtils.isEmpty(childId)) {
            binding.childIdInputLayout.setError("Please enter child's ID");
            return false;
        }

        binding.childIdInputLayout.setError(null);
        return true;
    }

    private void linkChildAccount(String childId) {
        // Show loading state
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.linkChildButton.setEnabled(false);
        updateLinkStatus("Checking account...", false);

        // First, check if the child ID exists in users collection
        db.collection("users").document(childId)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                    // Found in users collection, check if it's a child account
                    String userType = task.getResult().getString("userType");
                    String name = task.getResult().getString("name");
                    String email = task.getResult().getString("email");
                    
                    // Debug output
                    Log.d("AddChildActivity", "Found user: " + name + ", type: " + userType);
                    
                    // Consider it a child account if explicitly marked as child OR has no type yet
                    if ("child".equals(userType) || userType == null || userType.isEmpty()) {
                        // It's a child account in users collection, now proceed with linking
                        linkChildToParent(childId, name, email);
                    } else {
                        // Not a child account
                        binding.progressBar.setVisibility(View.GONE);
                        binding.linkChildButton.setEnabled(true);
                        updateLinkStatus("This ID belongs to a parent account", true);
                        Toast.makeText(AddChildActivity.this, 
                                "This ID does not belong to a child account", 
                                Toast.LENGTH_LONG).show();
                    }
                } else {
                    // Not found in users collection, try checking the children collection directly
                    db.collection("children").document(childId)
                        .get()
                        .addOnCompleteListener(childTask -> {
                            if (childTask.isSuccessful() && childTask.getResult() != null && 
                                childTask.getResult().exists()) {
                                // Found in children collection
                                String name = childTask.getResult().getString("name");
                                String email = childTask.getResult().getString("email");
                                
                                // Log what we found
                                Log.d("AddChildActivity", "Found child in children collection: " + name);
                                
                                // Check if the child is already linked to another parent
                                String existingParentId = childTask.getResult().getString("parentId");
                                if (existingParentId != null && !existingParentId.isEmpty() && 
                                    !existingParentId.equals(parentId)) {
                                    // Child already linked to another parent
                                    binding.progressBar.setVisibility(View.GONE);
                                    binding.linkChildButton.setEnabled(true);
                                    updateLinkStatus("This child is already linked to another parent", true);
                                    Toast.makeText(AddChildActivity.this,
                                            "This child is already linked to another parent",
                                            Toast.LENGTH_LONG).show();
                                } else {
                                    // Child exists in children collection but not linked or linked to this parent
                                    linkChildToParent(childId, name, email);
                                }
                            } else {
                                // As a last resort, check if the ID is a valid user ID at all
                                FirebaseAuth.getInstance().fetchSignInMethodsForEmail(childId + "@placeholder.com")
                                    .addOnCompleteListener(emailTask -> {
                                        // Just a check to make sure the ID format is valid
                                        // We can proceed anyway since we'll create the child entry
                                        binding.progressBar.setVisibility(View.GONE);
                                        binding.linkChildButton.setEnabled(true);
                                        updateLinkStatus("Creating new child entry with this ID", false);
                                        
                                        // Create a placeholder child entry with this ID
                                        linkChildToParent(childId, "Child", null);
                                    });
                            }
                        });
                }
            });
    }

    private void linkChildToParent(String childId, String name, String email) {
        // 1. Update the child account in users collection with the parent ID (if it exists)
        Map<String, Object> childUserUpdates = new HashMap<>();
        childUserUpdates.put("role", "child");
        childUserUpdates.put("linkedUserId", parentId);
        
        db.collection("users").document(childId)
            .update(childUserUpdates)
            .addOnSuccessListener(aVoid -> {
                // Success silently, continue with children collection
            })
            .addOnFailureListener(e -> {
                // User document might not exist, continue anyway with children collection
            })
            .addOnCompleteListener(task -> {
                // 2. Create or update the child document in children collection
                Map<String, Object> childData = new HashMap<>();
                childData.put("name", name != null ? name : "Child");
                childData.put("email", email != null ? email : "");
                childData.put("lastUpdated", System.currentTimeMillis());
                
                // Using set with merge option to create if doesn't exist or update if it does
                db.collection("children").document(childId)
                    .set(childData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        // Update the parent's user document
                        updateParentUserDocument(childId);
                    })
                    .addOnFailureListener(e -> {
                        // Error handling for children collection update
                        binding.progressBar.setVisibility(View.GONE);
                        binding.linkChildButton.setEnabled(true);
                        Toast.makeText(AddChildActivity.this, 
                                "Failed to create child profile: " + e.getMessage(), 
                                Toast.LENGTH_LONG).show();
                    });
            });
    }

    private void updateParentUserDocument(String childId) {
        // Get current parent data
        db.collection("users").document(parentId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                // Update parent document with new fields
                Map<String, Object> parentUpdates = new HashMap<>();
                
                // Ensure role is set to parent
                parentUpdates.put("role", "parent");
                parentUpdates.put("userType", "parent"); // For backward compatibility
                
                // Add child to childrenIds array if not already there
                List<String> childrenIds = new ArrayList<>();
                if (documentSnapshot.contains("childrenIds")) {
                    childrenIds = (List<String>) documentSnapshot.get("childrenIds");
                }
                
                if (childrenIds == null) {
                    childrenIds = new ArrayList<>();
                }
                
                if (!childrenIds.contains(childId)) {
                    childrenIds.add(childId);
                    parentUpdates.put("childrenIds", childrenIds);
                }
                
                // Keep linkedUserId for backward compatibility but ensure it has the latest child ID
                parentUpdates.put("linkedUserId", childId);
                
                db.collection("users").document(parentId)
                    .update(parentUpdates)
                    .addOnSuccessListener(aVoid -> {
                        binding.progressBar.setVisibility(View.GONE);
                        updateLinkStatus("Child linked successfully!", false);
                        
                        // Show success message and return to dashboard after 1.5 seconds
                        Toast.makeText(AddChildActivity.this, 
                                "Child linked successfully!", 
                                Toast.LENGTH_SHORT).show();
                        
                        new Handler().postDelayed(() -> finish(), 1500);
                    })
                    .addOnFailureListener(e -> {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.linkChildButton.setEnabled(true);
                        updateLinkStatus("Failed to update parent account: " + e.getMessage(), true);
                        Toast.makeText(AddChildActivity.this, 
                                "Failed to update parent account: " + e.getMessage(), 
                                Toast.LENGTH_LONG).show();
                    });
            })
            .addOnFailureListener(e -> {
                binding.progressBar.setVisibility(View.GONE);
                binding.linkChildButton.setEnabled(true);
                updateLinkStatus("Failed to retrieve parent data: " + e.getMessage(), true);
                Toast.makeText(AddChildActivity.this, 
                        "Failed to retrieve parent data: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
            });
    }

    private void updateLinkStatus(String message, boolean isError) {
        binding.linkStatus.setVisibility(View.VISIBLE);
        binding.linkStatus.setText(message);
        
        if (isError) {
            binding.linkStatus.setTextColor(getResources().getColor(R.color.service_inactive, null));
        } else {
            binding.linkStatus.setTextColor(getResources().getColor(R.color.service_active, null));
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
