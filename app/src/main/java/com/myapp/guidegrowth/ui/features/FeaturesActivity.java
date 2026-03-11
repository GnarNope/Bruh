package com.myapp.guidegrowth.ui.features;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.myapp.guidegrowth.R;
import com.myapp.guidegrowth.databinding.ActivityFeaturesBinding;
import com.myapp.guidegrowth.ui.auth.LoginActivity;
import com.myapp.guidegrowth.ui.child.ChildServiceManagerActivity;
import com.myapp.guidegrowth.ui.parent.AddChildActivity;
import com.myapp.guidegrowth.ui.parent.ParentDashboardActivity;
import com.myapp.guidegrowth.viewmodel.AuthViewModel;

public class FeaturesActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private ActivityFeaturesBinding binding;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    private String userType = "parent"; // Default to parent, will be updated based on who opens it

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFeaturesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Set up toolbar
        setSupportActionBar(binding.toolbar);

        // Get user type from intent
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("user_type")) {
            userType = intent.getStringExtra("user_type");
        }
        
        // Set up navigation drawer
        drawerLayout = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        
        toggle = new ActionBarDrawerToggle(
                this, drawerLayout, binding.toolbar, 
                R.string.navigation_drawer_open, 
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        
        // Set the appropriate menu based on user type
        if ("child".equals(userType)) {
            navigationView.getMenu().clear();
            navigationView.inflateMenu(R.menu.menu_child_navigation);
        } else {
            navigationView.getMenu().clear();
            navigationView.inflateMenu(R.menu.menu_parent_navigation);
        }
        
        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        Intent intent = null;
        
        // Handle navigation for parent
        if ("parent".equals(userType)) {
            if (id == R.id.nav_dashboard) {
                intent = new Intent(this, ParentDashboardActivity.class);
            } else if (id == R.id.nav_add_child) {
                intent = new Intent(this, AddChildActivity.class);
            } else if (id == R.id.nav_features) {
                // Already on features page
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            } else if (id == R.id.nav_settings) {
                // Show settings dialog or navigate to settings activity
            } else if (id == R.id.nav_logout) {
                // Log out
                new AuthViewModel().logout();
                intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            }
        } 
        // Handle navigation for child
        else {
            if (id == R.id.nav_child_dashboard) {
                intent = new Intent(this, ChildServiceManagerActivity.class);
            } else if (id == R.id.nav_permissions) {
                // Navigate to permissions manager
                intent = new Intent(this, com.myapp.guidegrowth.ChildServiceManagerActivity.class);
            } else if (id == R.id.nav_child_features) {
                // Already on features page
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            } else if (id == R.id.nav_child_settings) {
                // Show settings dialog or navigate to settings activity
            } else if (id == R.id.nav_child_logout) {
                // Log out
                new AuthViewModel().logout();
                intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            }
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
}
