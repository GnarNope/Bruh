package com.myapp.guidegrowth.viewmodel;

import android.text.TextUtils;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.myapp.guidegrowth.model.User;
import com.myapp.guidegrowth.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;

public class UserViewModel extends ViewModel {
    
    private UserRepository userRepository;
    private MutableLiveData<Boolean> profileUpdateResult = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    
    public UserViewModel() {
        userRepository = UserRepository.getInstance();
    }
    
    public LiveData<User> getCurrentUser() {
        return userRepository.getCurrentUser();
    }
    
    public void updateUserProfile(String name, String userType) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        
        if (currentUser == null) {
            errorMessage.setValue("No user logged in");
            profileUpdateResult.setValue(false);
            return;
        }
        
        String uid = currentUser.getUid();
        DocumentReference userRef = FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid);
        
        Map<String, Object> updates = new HashMap<>();
        if (name != null && !name.isEmpty()) {
            updates.put("name", name);
        }
        if (userType != null && !userType.isEmpty()) {
            updates.put("userType", userType);
            updates.put("role", userType); // Store role in separate field for clarity
        }
        
        userRef.update(updates)
                .addOnSuccessListener(aVoid -> {
                    profileUpdateResult.setValue(true);
                    // Reload the user data
                    userRepository.loadUserData(uid, null);
                })
                .addOnFailureListener(e -> {
                    errorMessage.setValue(e.getMessage());
                    profileUpdateResult.setValue(false);
                });
    }
    
    public void updateUserProfileWithConnection(String name, String userType, String connectionId, boolean isParent) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        
        if (currentUser == null) {
            errorMessage.setValue("No user logged in");
            profileUpdateResult.setValue(false);
            return;
        }
        
        String uid = currentUser.getUid();
        DocumentReference userRef = FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid);
        
        Map<String, Object> updates = new HashMap<>();
        if (name != null && !name.isEmpty()) {
            updates.put("name", name);
        }
        
        if (userType != null && !userType.isEmpty()) {
            updates.put("userType", userType); // For backward compatibility
            updates.put("role", userType); // New field for role
        }
        
        // Store connection ID with the new structure
        if (!TextUtils.isEmpty(connectionId)) {
            updates.put("linkedUserId", connectionId);
            
            // For child accounts, also ensure there's a document in the children collection
            if (!isParent) {
                Map<String, Object> childData = new HashMap<>();
                childData.put("name", name);
                childData.put("email", currentUser.getEmail());
                childData.put("lastUpdated", System.currentTimeMillis());
                
                FirebaseFirestore.getInstance()
                    .collection("children")
                    .document(uid)
                    .set(childData, com.google.firebase.firestore.SetOptions.merge());
            }
        }
        
        userRef.update(updates)
                .addOnSuccessListener(aVoid -> {
                    // If this is a child account linking to a parent, also update the parent's account
                    if (!isParent && !TextUtils.isEmpty(connectionId)) {
                        updateConnectedUserDocument(uid, connectionId, isParent);
                    } else {
                        profileUpdateResult.setValue(true);
                        // Reload the user data
                        userRepository.loadUserData(uid, null);
                    }
                })
                .addOnFailureListener(e -> {
                    errorMessage.setValue(e.getMessage());
                    profileUpdateResult.setValue(false);
                });
    }
    
    private void updateConnectedUserDocument(String currentUserId, String connectionId, boolean isParent) {
        DocumentReference connectionRef = FirebaseFirestore.getInstance()
                .collection("users")
                .document(connectionId);
        
        Map<String, Object> updates = new HashMap<>();
        
        // Using the new structure for linked users
        updates.put("linkedUserId", currentUserId);
        
        connectionRef.update(updates)
                .addOnSuccessListener(aVoid -> {
                    profileUpdateResult.setValue(true);
                    // Reload the user data
                    userRepository.loadUserData(currentUserId, null);
                })
                .addOnFailureListener(e -> {
                    // It's okay if this fails - the primary user's data was updated
                    profileUpdateResult.setValue(true);
                    userRepository.loadUserData(currentUserId, null);
                });
    }
    
    public LiveData<Boolean> getProfileUpdateResult() {
        return profileUpdateResult;
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
}
