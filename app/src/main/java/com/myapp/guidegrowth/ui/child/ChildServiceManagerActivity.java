package com.myapp.guidegrowth.ui.child;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.myapp.guidegrowth.R;
import com.myapp.guidegrowth.databinding.ActivityChildServiceManagerBinding;
import com.myapp.guidegrowth.model.User;
import com.myapp.guidegrowth.service.ChildMonitorService;
import com.myapp.guidegrowth.ui.auth.LoginActivity;
import com.myapp.guidegrowth.ui.features.FeaturesActivity;
import com.myapp.guidegrowth.utils.FirestoreManager;
import com.myapp.guidegrowth.viewmodel.AuthViewModel;
import com.myapp.guidegrowth.viewmodel.UserViewModel;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChildServiceManagerActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    
    private ActivityChildServiceManagerBinding binding;
    private AuthViewModel authViewModel;
    private UserViewModel userViewModel;
    private Button toggleServiceButton;
    private TextView serviceStatusText;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChildServiceManagerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Set up toolbar
        setSupportActionBar(binding.toolbar);
        
        // Set up navigation drawer
        drawerLayout = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        
        toggle = new ActionBarDrawerToggle(
                this, drawerLayout, binding.toolbar, 
                R.string.navigation_drawer_open, 
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        
        navigationView.setNavigationItemSelectedListener(this);
        
        setupViewModels();
        initializeViews();
        loadUserData();
        setupListeners();
        setupServiceToggleButton();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        Intent intent = null;
        
        if (id == R.id.nav_child_dashboard) {
            // Already on dashboard
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        } else if (id == R.id.nav_permissions) {
            intent = new Intent(this, com.myapp.guidegrowth.ChildServiceManagerActivity.class);
        } else if (id == R.id.nav_child_features) {
            intent = new Intent(this, FeaturesActivity.class);
            intent.putExtra("user_type", "child");
        } else if (id == R.id.nav_child_settings) {
            showSettingsDialog();
        } else if (id == R.id.nav_child_logout) {
            logout();
        }
        
        // Start activity if intent was set
        if (intent != null) {
            startActivity(intent);
        }
        
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
    
    private void setupViewModels() {
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        
        userViewModel.getCurrentUser().observe(this, user -> {
            if (user != null) {
                updateUI(user);
            }
        });
    }
    
    private void initializeViews() {
        toggleServiceButton = findViewById(R.id.toggleServiceButton);
        serviceStatusText = findViewById(R.id.serviceStatusText);
    }

    private void loadUserData() {
        binding.progressBar.setVisibility(View.VISIBLE);
        
        authViewModel.getCurrentUser().observe(this, user -> {
            binding.progressBar.setVisibility(View.GONE);
            
            if (user != null) {
                updateUI(user);
            } else {
                navigateToLogin();
            }
        });
    }

    private void updateUI(User user) {
        binding.welcomeText.setText("Hello, " + user.getName());
        
        String parentId = user.getParentId();
        String linkedUserId = user.getLinkedUserId();
        
        if ((parentId != null && !parentId.isEmpty()) || (linkedUserId != null && !linkedUserId.isEmpty())) {
            // Only check parent commands if service isn't running
            if (!isChildMonitoringServiceRunning()) {
                String parentIdToUse = (parentId != null && !parentId.isEmpty()) ? parentId : linkedUserId;
                
                // Use FirestoreManager's cached document to prevent unnecessary reads
                FirestoreManager.getInstance().getCachedDocument(
                    "children", 
                    authViewModel.getCurrentUserId(),
                    new FirestoreManager.DocumentCallback() {
                        @Override
                        public void onDocumentData(Map<String, Object> data, boolean fromCache) {
                            // Only proceed if we have data
                            if (data != null) {
                                // Check for pending commands
                                FirestoreManager.getInstance().getCachedDocument(
                                    "children/" + authViewModel.getCurrentUserId() + "/commands",
                                    "latest",
                                    new FirestoreManager.DocumentCallback() {
                                        @Override
                                        public void onDocumentData(Map<String, Object> commandData, boolean fromCommandCache) {
                                            if (commandData != null) {
                                                Boolean lockCommand = (Boolean) commandData.get("lockCommand");
                                                String warning = (String) commandData.get("warning");
                                                
                                                if (Boolean.TRUE.equals(lockCommand)) {
                                                    Snackbar.make(
                                                        binding.getRoot(),
                                                        "Lock command received from parent",
                                                        Snackbar.LENGTH_LONG
                                                    ).show();
                                                }
                                                
                                                if (warning != null && !warning.isEmpty()) {
                                                    Snackbar.make(
                                                        binding.getRoot(),
                                                        "Message from parent: " + warning,
                                                        Snackbar.LENGTH_LONG
                                                    ).setBackgroundTint(getResources().getColor(R.color.warning_color))
                                                    .show();
                                                }
                                            }
                                        }

                                        @Override
                                        public void onError(String errorMessage) {
                                            // Silently fail, this is just UI notification
                                        }
                                    }
                                );
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            // Silently fail, this is just UI notification
                        }
                    }
                );
            }
        } 
        
        // Set the service status UI elements
        updateServiceStatusUI(isChildMonitoringServiceRunning());
    }

    private void setupListeners() {
        binding.linkParentButton.setOnClickListener(v -> {
            showParentLinkDialog();
        });
        
        binding.setupPermissionsButton.setOnClickListener(v -> {
            openPermissionsManager();
        });
        
        binding.goToPermissionsButton.setOnClickListener(v -> {
            openPermissionsManager();
        });
    }
    
    private void openPermissionsManager() {
        Intent intent = new Intent(this, com.myapp.guidegrowth.ChildServiceManagerActivity.class);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_logout) {
            logout();
            return true;
        } else if (id == R.id.action_settings) {
            showSettingsDialog();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void showSettingsDialog() {
        String userId = authViewModel.getCurrentUserId();
        
        if (userId == null || userId.isEmpty()) {
            Snackbar.make(binding.getRoot(), "User ID not available", Snackbar.LENGTH_SHORT).show();
            return;
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Your User ID");

        View settingsView = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        builder.setView(settingsView);

        TextView userIdText = settingsView.findViewById(R.id.text_user_id);
        Button copyButton = settingsView.findViewById(R.id.button_copy_id);

        userIdText.setText(userId);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        copyButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("User ID", userId);
            clipboard.setPrimaryClip(clip);
            
            Snackbar.make(
                binding.getRoot(), 
                "ID copied to clipboard. Share this with your parent.", 
                Snackbar.LENGTH_SHORT
            ).show();
        });
    }

    private void showParentLinkDialog() {
        View linkView = getLayoutInflater().inflate(R.layout.dialog_link_parent, null);
        TextView userIdTextView = linkView.findViewById(R.id.text_user_id);
        Button copyIdButton = linkView.findViewById(R.id.button_copy_id);
        TextInputEditText parentIdInput = linkView.findViewById(R.id.parent_id_input);

        // Get current user ID from Firebase Auth instead of ViewModel
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        userIdTextView.setText(userId);

        // Copy ID button
        copyIdButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("User ID", userId);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "ID copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        new MaterialAlertDialogBuilder(this)
                .setView(linkView)
                .setNeutralButton("Cancel", null)
                .setPositiveButton("Link", (dialog, which) -> {
                    String parentId = parentIdInput.getText() != null ? 
                            parentIdInput.getText().toString().trim() : "";
                    
                    if (!TextUtils.isEmpty(parentId)) {
                        linkToParent(parentId);
                    }
                })
                .show();
    }

    private void linkToParent(String parentId) {
        if (parentId == null || parentId.isEmpty()) {
            Toast.makeText(this, "Please enter a valid parent ID", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress
        Snackbar.make(binding.getRoot(), "Linking to parent...", Snackbar.LENGTH_LONG).show();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        // Get current user ID from Firebase Auth instead of ViewModel
        String childId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Verify parent exists
        db.collection("users").document(parentId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this, "Parent ID not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    FirestoreManager firestoreManager = FirestoreManager.getInstance();
                    
                    // Update child's parent reference directly
                    Map<String, Object> childUpdates = new HashMap<>();
                    childUpdates.put("parentId", parentId);
                    childUpdates.put("linkedToParent", true);
                    childUpdates.put("parentName", documentSnapshot.getString("name"));
                    
                    // Update the child document
                    db.collection("users").document(childId)
                       .update(childUpdates)
                       .addOnSuccessListener(aVoid -> {
                           // Now update parent's children list
                           db.collection("users").document(parentId).get()
                             .addOnSuccessListener(parentDoc -> {
                                 Map<String, Object> parentData = parentDoc.getData();
                                 List<String> childrenIds = new ArrayList<>();

                                 // Get existing children IDs or create new list
                                 if (parentData != null && parentData.containsKey("childrenIds")) {
                                    childrenIds = (List<String>) parentData.get("childrenIds");
                                 }

                                 if (childrenIds == null) {
                                    childrenIds = new ArrayList<>();
                                 }

                                 // Add this child if not already in list
                                 if (!childrenIds.contains(childId)) {
                                    childrenIds.add(childId);
                                 }

                                 Map<String, Object> parentUpdates = new HashMap<>();
                                 parentUpdates.put("childrenIds", childrenIds);
                                 parentUpdates.put("hasChildren", true);

                                 // Update the parent document
                                 db.collection("users").document(parentId)
                                   .update(parentUpdates)
                                   .addOnSuccessListener(aVoid2 -> {
                                       // Success
                                       Toast.makeText(this, "Successfully linked to parent!", Toast.LENGTH_SHORT).show();
                                     
                                       // Refresh user data
                                       authViewModel.refreshCurrentUser();
                                   })
                                   .addOnFailureListener(e -> {
                                       // Error updating parent
                                       Toast.makeText(this, "Error updating parent: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                   });
                             })
                             .addOnFailureListener(e -> {
                                 Toast.makeText(this, "Error checking parent: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                             });
                       })
                       .addOnFailureListener(e -> {
                           // Error updating child
                           Toast.makeText(this, "Error updating child: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                       });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void logout() {
        // Stop the service if it's running
        if (isChildMonitoringServiceRunning()) {
            stopChildMonitoringService();
        }
        
        authViewModel.logout();
        navigateToLogin();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private boolean areAllPermissionsGranted() {
        boolean locationPermission = ContextCompat.checkSelfPermission(this, 
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        
        boolean backgroundLocationPermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationPermission = ContextCompat.checkSelfPermission(this, 
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        
        AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, 
                android.os.Process.myUid(), getPackageName());
        boolean usageStatsPermission = mode == AppOpsManager.MODE_ALLOWED;
        
        boolean overlayPermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            overlayPermission = Settings.canDrawOverlays(this);
        }
        
        return locationPermission && backgroundLocationPermission && 
               usageStatsPermission && overlayPermission;
    }

    private void startChildMonitoringService() {
        if (areAllPermissionsGranted()) {
            Intent serviceIntent = new Intent(this, ChildMonitorService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            
            updateServiceStatusUI(true);
            
            Snackbar.make(
                binding.getRoot(),
                "Monitoring service started successfully", 
                Snackbar.LENGTH_SHORT
            ).show();
        } else {
            Snackbar.make(
                binding.getRoot(),
                "All permissions must be granted to start monitoring", 
                Snackbar.LENGTH_LONG
            ).show();
            Intent intent = new Intent(this, com.myapp.guidegrowth.ChildServiceManagerActivity.class);
            startActivity(intent);
        }
    }

    private void stopChildMonitoringService() {
        Intent serviceIntent = new Intent(this, ChildMonitorService.class);
        stopService(serviceIntent);
        updateServiceStatusUI(false);
        
        Snackbar.make(
            binding.getRoot(),
            "Monitoring service stopped", 
            Snackbar.LENGTH_SHORT
        ).show();
    }

    private void updateServiceStatusUI(boolean isRunning) {
        if (isRunning) {
            serviceStatusText.setText("Monitoring Active");
            serviceStatusText.setTextColor(getResources().getColor(R.color.service_active));
            toggleServiceButton.setText("Stop Monitoring");
        } else {
            serviceStatusText.setText("Monitoring Inactive");
            serviceStatusText.setTextColor(getResources().getColor(R.color.service_inactive));
            toggleServiceButton.setText("Start Monitoring");
        }
    }

    private boolean isChildMonitoringServiceRunning() {
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        
        for (ActivityManager.RunningServiceInfo service : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (ChildMonitorService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void setupServiceToggleButton() {
        updateServiceStatusUI(isChildMonitoringServiceRunning());
        
        toggleServiceButton.setOnClickListener(v -> {
            if (isChildMonitoringServiceRunning()) {
                stopChildMonitoringService();
            } else {
                startChildMonitoringService();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        if (authViewModel != null) {
            authViewModel.refreshCurrentUser();
            loadUserData();
        }
        
        // Update service status when activity resumes
        updateServiceStatusUI(isChildMonitoringServiceRunning());
    }
}
