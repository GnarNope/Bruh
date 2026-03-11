package com.myapp.guidegrowth.ui.parent;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.myapp.guidegrowth.R;
import com.myapp.guidegrowth.adapter.ParentPagerAdapter;
import com.myapp.guidegrowth.databinding.ActivityParentDashboardBinding;
import com.myapp.guidegrowth.model.Child;
import com.myapp.guidegrowth.model.User;
import com.myapp.guidegrowth.ui.auth.LoginActivity;
import com.myapp.guidegrowth.ui.features.FeaturesActivity;
import com.myapp.guidegrowth.utils.CommandUtils;
import com.myapp.guidegrowth.utils.FirestoreWriteOptimizer;
import com.myapp.guidegrowth.viewmodel.AuthViewModel;
import com.myapp.guidegrowth.viewmodel.ChildViewModel;
import com.myapp.guidegrowth.viewmodel.UserViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParentDashboardActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    
    private ActivityParentDashboardBinding binding;
    private AuthViewModel authViewModel;
    private UserViewModel userViewModel;
    private ChildViewModel childViewModel;
    private ParentPagerAdapter pagerAdapter;
    private List<Child> childList = new ArrayList<>();
    private String selectedChildId;
    private FirestoreWriteOptimizer writeOptimizer;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityParentDashboardBinding.inflate(getLayoutInflater());
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
        
        // Setup empty adapter initially
        pagerAdapter = new ParentPagerAdapter(this, null);
        binding.viewPager.setAdapter(pagerAdapter);
        
        // Lock ViewPager when there's no child selected
        binding.viewPager.setUserInputEnabled(false);
        
        // Connect TabLayout with ViewPager2
        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0:
                            tab.setText("Location");
                            tab.setIcon(R.drawable.ic_location);
                            break;
                        case 1:
                            tab.setText("App Usage");
                            tab.setIcon(R.drawable.ic_apps);
                            break;
                        case 2:
                            tab.setText("Controls");
                            tab.setIcon(R.drawable.ic_controls);
                            break;
                    }
                }).attach();
        
        writeOptimizer = FirestoreWriteOptimizer.getInstance(this);
        loadUserData();
        setupListeners();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        Intent intent = null;
        
        if (id == R.id.nav_dashboard) {
            // Already on dashboard
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        } else if (id == R.id.nav_add_child) {
            intent = new Intent(this, AddChildActivity.class);
        } else if (id == R.id.nav_features) {
            intent = new Intent(this, FeaturesActivity.class);
            intent.putExtra("user_type", "parent");
        } else if (id == R.id.nav_settings) {
            showSettingsDialog();
        } else if (id == R.id.nav_logout) {
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
        childViewModel = new ViewModelProvider(this).get(ChildViewModel.class);
        
        // Observe children data
        childViewModel.getChildrenList().observe(this, children -> {
            if (children != null && !children.isEmpty()) {
                childList = children;
                updateChildSelector();
                
                // Select first child by default
                if (selectedChildId == null && !childList.isEmpty()) {
                    selectChild(childList.get(0));
                }
            } else {
                showNoChildrenUI();
            }
        });
    }

    private void loadUserData() {
        // Show loading indicator
        binding.progressBar.setVisibility(View.VISIBLE);
        
        // Add a timeout handler to prevent indefinite loading
        new android.os.Handler().postDelayed(() -> {
            if (binding.progressBar.getVisibility() == View.VISIBLE) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Loading timed out. Please check your connection.", Toast.LENGTH_SHORT).show();
            }
        }, 10000); // 10 second timeout
        
        // Log for debugging
        android.util.Log.d("ParentDashboard", "Loading user data");
        
        String currentUserId = authViewModel.getCurrentUserId();
        if (currentUserId == null) {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Unable to get current user. Please login again.", Toast.LENGTH_SHORT).show();
            navigateToLogin();
            return;
        }
        
        // Direct Firestore query to ensure we get data
        FirebaseFirestore.getInstance().collection("users").document(currentUserId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                binding.progressBar.setVisibility(View.GONE);
                
                if (documentSnapshot.exists()) {
                    // Process user data
                    String name = documentSnapshot.getString("name");
                    if (name != null) {
                        binding.welcomeText.setText("Welcome, " + name);
                    }
                    
                    // Check for children
                    List<String> childrenIds = (List<String>) documentSnapshot.get("childrenIds");
                    
                    if (childrenIds != null && !childrenIds.isEmpty()) {
                        // Load children directly without going through ViewModel for now
                        loadChildrenDirectly(childrenIds);
                    } else {
                        showNoChildrenUI();
                    }
                } else {
                    Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show();
                    navigateToLogin();
                }
            })
            .addOnFailureListener(e -> {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Failed to load user data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                android.util.Log.e("ParentDashboard", "Error loading user data", e);
            });
    }

    private void loadChildrenDirectly(List<String> childIds) {
        List<Child> children = new ArrayList<>();
        final int[] completedQueries = {0};
        final int totalQueries = childIds.size();
        
        for (String childId : childIds) {
            FirebaseFirestore.getInstance().collection("children").document(childId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        String email = documentSnapshot.getString("email");
                        
                        Child child = new Child(childId, name != null ? name : "Unknown Child", email);
                        
                        // Set other fields if available
                        if (documentSnapshot.contains("lastUpdated")) {
                            Long lastUpdated = documentSnapshot.getLong("lastUpdated");
                            if (lastUpdated != null) {
                                child.setLastUpdated(lastUpdated);
                            }
                        }
                        
                        children.add(child);
                    }
                    
                    // Always count as completed even if child not found
                    completedQueries[0]++;
                    
                    if (completedQueries[0] >= totalQueries) {
                        if (!children.isEmpty()) {
                            childList = children;
                            updateChildSelector();
                            selectChild(childList.get(0));
                        } else {
                            showNoChildrenUI();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ParentDashboard", "Error loading child: " + childId, e);
                    completedQueries[0]++;
                    
                    if (completedQueries[0] >= totalQueries) {
                        if (!children.isEmpty()) {
                            childList = children;
                            updateChildSelector();
                            selectChild(childList.get(0));
                        } else {
                            showNoChildrenUI();
                        }
                    }
                });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Verify authentication state on start
        if (authViewModel.getCurrentUserId() == null) {
            navigateToLogin();
        }
    }

    private void updateUI(User user) {
        binding.welcomeText.setText("Welcome, " + user.getName());
    }
    
    private void showNoChildrenUI() {
        binding.noChildLinkedCard.setVisibility(View.VISIBLE);
        binding.childSelectorContainer.setVisibility(View.GONE);
        binding.tabLayout.setVisibility(View.GONE);
        binding.viewPager.setVisibility(View.GONE);
    }
    
    private void showChildMonitoringUI() {
        binding.noChildLinkedCard.setVisibility(View.GONE);
        binding.childSelectorContainer.setVisibility(View.VISIBLE);
        binding.tabLayout.setVisibility(View.VISIBLE);
        binding.viewPager.setVisibility(View.VISIBLE);
    }
    
    private void updateChildSelector() {
        if (childList.isEmpty()) {
            showNoChildrenUI();
            return;
        }
        showChildMonitoringUI();
        
        // Update the child selector dropdown
        binding.childSelector.removeAllViews();
        
        for (Child child : childList) {
            ChildChipView chipView = new ChildChipView(this);
            chipView.setChild(child);
            chipView.setOnClickListener(v -> selectChild(child));
            binding.childSelector.addView(chipView);
        }
    }
    
    private void selectChild(Child child) {
        // Update selected child
        selectedChildId = child.getId();
        
        // Highlight the selected child chip
        for (int i = 0; i < binding.childSelector.getChildCount(); i++) {
            View view = binding.childSelector.getChildAt(i);
            if (view instanceof ChildChipView) {
                ChildChipView chipView = (ChildChipView) view;
                chipView.setSelected(chipView.getChild().getId().equals(selectedChildId));
            }
        }
        
        // Update the ViewPager adapter with the selected child
        pagerAdapter = new ParentPagerAdapter(this, child);
        binding.viewPager.setAdapter(pagerAdapter);
        binding.viewPager.setUserInputEnabled(true);
        
        // Set title
        binding.selectedChildName.setText(child.getName());
    }

    private void setupListeners() {
        binding.addChildButton.setOnClickListener(v -> {
            navigateToAddChild();
        });
        binding.fabAddChild.setOnClickListener(v -> {
            navigateToAddChild();
        });
        
        if (binding.locateChildButton != null) {
            binding.locateChildButton.setOnClickListener(v -> {
                locateChildOnMap(selectedChildId);
            });
        }
    }

    private void navigateToAddChild() {
        Intent intent = new Intent(this, AddChildActivity.class);
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
        // Get current user ID
        String userId = authViewModel.getCurrentUserId();
        
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "User ID not available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create an AlertDialog builder
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Settings");

        // Inflate the settings dialog layout
        View settingsView = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        builder.setView(settingsView);

        // Find views in the dialog
        TextView userIdText = settingsView.findViewById(R.id.text_user_id);
        Button copyButton = settingsView.findViewById(R.id.button_copy_id);

        // Set the user ID
        userIdText.setText(userId);

        // Create and show the dialog
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        // Set up copy button
        copyButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("User ID", userId);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "ID copied to clipboard", Toast.LENGTH_SHORT).show();
        });
    }

    private void logout() {
        authViewModel.logout();
        navigateToLogin();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    // Method to send command to child's device
    public void sendCommandToChild(String commandType, Object commandData) {
        if (selectedChildId == null) {
            Toast.makeText(this, "No child selected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading indicator
        binding.progressBar.setVisibility(View.VISIBLE);
        
        Map<String, Object> command;
        
        // Use specialized command creator for usage stats requests
        if ("check_in".equals(commandType) && commandData instanceof Map && 
            ((Map)commandData).containsKey("updateType") && 
            "usage_stats".equals(((Map)commandData).get("updateType"))) {
            
            command = CommandUtils.createUsageStatsCommand();
        } else {
            // Create standard command
            Map<String, Object> additionalData = new HashMap<>();
            
            // Add command specific data
            if (commandData != null) {
                if (commandData instanceof String) {
                    additionalData.put(CommandUtils.FIELD_MESSAGE, commandData);
                } else if (commandData instanceof Map) {
                    additionalData.putAll((Map) commandData);
                }
            }
            
            command = CommandUtils.createCommand(commandType, "");
            command.putAll(additionalData);
        }
        
        // Send command with high priority for immediate processing
        FirebaseFirestore.getInstance()
            .collection("children")
            .document(selectedChildId)
            .collection("commands")
            .document("latest")
            .set(command)
            .addOnSuccessListener(aVoid -> {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(ParentDashboardActivity.this, 
                        "Command sent successfully", Toast.LENGTH_SHORT).show();
                
                // Wait for status updates
                waitForCommandStatus();
            })
            .addOnFailureListener(e -> {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(ParentDashboardActivity.this, 
                        "Failed to send command: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
    
    private void waitForCommandStatus() {
        if (selectedChildId == null) return;
        
        final Handler handler = new Handler(Looper.getMainLooper());
        final int[] attempts = {0};
        final int maxAttempts = 10;
        
        Runnable checkStatus = new Runnable() {
            @Override
            public void run() {
                if (attempts[0]++ >= maxAttempts) {
                    // Stop checking after max attempts
                    return;
                }
                
                FirebaseFirestore.getInstance()
                    .collection("children")
                    .document(selectedChildId)
                    .collection("commands")
                    .document("latest")
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String status = documentSnapshot.getString(CommandUtils.FIELD_STATUS);
                            if (CommandUtils.STATUS_PROCESSED.equals(status)) {
                                Toast.makeText(ParentDashboardActivity.this, 
                                        "Command processed by child device", Toast.LENGTH_SHORT).show();
                            } else if (CommandUtils.STATUS_FAILED.equals(status)) {
                                String error = documentSnapshot.getString(CommandUtils.FIELD_ERROR);
                                Toast.makeText(ParentDashboardActivity.this, 
                                        "Command failed on child device: " + error, Toast.LENGTH_LONG).show();
                            } else if (attempts[0] < maxAttempts) {
                                // Keep checking
                                handler.postDelayed(this, 2000);
                            }
                        }
                    });
            }
        };
        
        // Start checking command status after a short delay
        handler.postDelayed(checkStatus, 2000);
    }
    
    /**
     * Retrieves the current location of a child from Firestore and opens it in Google Maps
     * @param childId The ID of the child whose location we want to retrieve
     */
    public void locateChildOnMap(String childId) {
        if (childId == null || childId.isEmpty()) {
            if (selectedChildId == null) {
                Toast.makeText(this, "No child selected", Toast.LENGTH_SHORT).show();
                return;
            }
            childId = selectedChildId;
        }
        
        // Show loading indicator
        binding.progressBar.setVisibility(View.VISIBLE);
        
        // Reference to the child's current location document
        DocumentReference locationRef = FirebaseFirestore.getInstance()
                .collection("children")
                .document(childId)
                .collection("location")
                .document("current");
        
        locationRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                DocumentSnapshot document = task.getResult();
                
                if (document.exists()) {
                    // Get latitude and longitude from the document
                    Double latitude = document.getDouble("latitude");
                    Double longitude = document.getDouble("longitude");
                    Long timestamp = document.getLong("timestamp");
                    
                    if (latitude != null && longitude != null) {
                        // Calculate how recent the location is
                        long ageMinutes = timestamp != null ? 
                                (System.currentTimeMillis() - timestamp) / 60000 : 0;
                        
                        // Open Google Maps app with the location
                        openLocationInMaps(latitude, longitude);
                        
                        // Always request updated location from child device
                        sendCommandToChild(CommandUtils.CMD_REQUEST_LOCATION, null);
                        
                        // Show location age to user
                        if (ageMinutes > 10) {
                            Toast.makeText(this, 
                                    "Warning: Location is " + ageMinutes + " minutes old. Requesting new location...", 
                                    Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(this, "Location data is incomplete", Toast.LENGTH_SHORT).show();
                        // Request location
                        sendCommandToChild(CommandUtils.CMD_REQUEST_LOCATION, null);
                    }
                } else {
                    Toast.makeText(this, "No location data available for this child", Toast.LENGTH_SHORT).show();
                    // Request location
                    sendCommandToChild(CommandUtils.CMD_REQUEST_LOCATION, null);
                }
            } else {
                Toast.makeText(this, "Failed to retrieve location: " + 
                        (task.getException() != null ? task.getException().getMessage() : "Unknown error"), 
                        Toast.LENGTH_SHORT).show();
            }
            
            binding.progressBar.setVisibility(View.GONE);
        });
    }
    
    /**
     * Opens Google Maps application with the specified coordinates
     * 
     * @param latitude The latitude coordinate
     * @param longitude The longitude coordinate
     */
    private void openLocationInMaps(double latitude, double longitude) {
        // Create a Uri from a latitude and longitude
        Uri locationUri = Uri.parse("geo:" + latitude + "," + longitude + "?q=" + latitude + "," + longitude + "(Child's Location)");
        
        // Create an Intent with the action to view
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, locationUri);
        
        // Make the intent specific to Google Maps if available
        mapIntent.setPackage("com.google.android.apps.maps");
        
        // Check if Google Maps is installed
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            // If Google Maps isn't installed, open in browser
            Uri browserUri = Uri.parse("https://www.google.com/maps?q=" + latitude + "," + longitude);
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, browserUri);
            startActivity(browserIntent);
        }
    }
}
