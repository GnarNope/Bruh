package com.myapp.guidegrowth.repository;

import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.myapp.guidegrowth.model.User;
import com.myapp.guidegrowth.utils.FirebaseAuthManager;
import com.myapp.guidegrowth.utils.FirestoreManager;

/**
 * Repository for User-related data operations
 */
public class UserRepository {
    private static UserRepository instance;
    private FirebaseAuthManager authManager;
    private FirestoreManager firestoreManager;
    
    private MutableLiveData<User> currentUser = new MutableLiveData<>();
    
    private UserRepository() {
        authManager = FirebaseAuthManager.getInstance();
        firestoreManager = FirestoreManager.getInstance();
    }
    
    public static synchronized UserRepository getInstance() {
        if (instance == null) {
            instance = new UserRepository();
        }
        return instance;
    }
    
    public void registerUser(String email, String password, String name, String userType, OnUserRegistrationListener listener) {
        authManager.registerUser(email, password, task -> {
            if (task.isSuccessful()) {
                FirebaseUser firebaseUser = task.getResult().getUser();
                User user = new User(
                        firebaseUser.getUid(),
                        email,
                        name,
                        userType,
                        System.currentTimeMillis()
                );
                
                // Save user data to Firestore
                firestoreManager.getDocumentReference(FirestoreManager.COLLECTION_USERS, firebaseUser.getUid())
                        .set(user.toMap())
                        .addOnSuccessListener(aVoid -> {
                            currentUser.setValue(user);
                            if (listener != null) {
                                listener.onSuccess(user);
                            }
                        })
                        .addOnFailureListener(e -> {
                            if (listener != null) {
                                listener.onFailure(e.getMessage());
                            }
                        });
            } else {
                if (listener != null) {
                    listener.onFailure(task.getException().getMessage());
                }
            }
        });
    }
    
    public void loginUser(String email, String password, OnUserLoginListener listener) {
        authManager.loginUser(email, password, task -> {
            if (task.isSuccessful()) {
                FirebaseUser firebaseUser = task.getResult().getUser();
                loadUserData(firebaseUser.getUid(), listener);
            } else {
                if (listener != null) {
                    listener.onFailure(task.getException().getMessage());
                }
            }
        });
    }
    
    public void loadUserData(String userId, OnUserLoginListener listener) {
        DocumentReference userRef = firestoreManager.getDocumentReference(
                FirestoreManager.COLLECTION_USERS, userId);
        
        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    User user = document.toObject(User.class);
                    // Update last login time
                    user.setLastLoginAt(System.currentTimeMillis());
                    userRef.update("lastLoginAt", user.getLastLoginAt());
                    
                    currentUser.setValue(user);
                    if (listener != null) {
                        listener.onSuccess(user);
                    }
                } else {
                    if (listener != null) {
                        listener.onFailure("User data not found");
                    }
                }
            } else {
                if (listener != null) {
                    listener.onFailure(task.getException().getMessage());
                }
            }
        });
    }
    
    public void logoutUser() {
        authManager.logoutUser();
        currentUser.setValue(null);
    }
    
    public void updateUserFCMToken(String token) {
        FirebaseUser firebaseUser = authManager.getCurrentUser();
        if (firebaseUser != null) {
            firestoreManager.getDocumentReference(FirestoreManager.COLLECTION_USERS, firebaseUser.getUid())
                    .update("fcmToken", token);
        }
    }
    
    public MutableLiveData<User> getCurrentUser() {
        return currentUser;
    }
    
    public boolean isUserLoggedIn() {
        return authManager.isUserLoggedIn();
    }
    
    /**
     * Gets the FirebaseAuthManager instance
     * @return The FirebaseAuthManager instance
     */
    public FirebaseAuthManager getAuthManager() {
        return authManager;
    }
    
    public interface OnUserRegistrationListener {
        void onSuccess(User user);
        void onFailure(String errorMessage);
    }
    
    public interface OnUserLoginListener {
        void onSuccess(User user);
        void onFailure(String errorMessage);
    }
}
