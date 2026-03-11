package com.myapp.guidegrowth.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.myapp.guidegrowth.model.User;
import com.myapp.guidegrowth.repository.UserRepository;
import com.myapp.guidegrowth.utils.FCMManager;

/**
 * ViewModel for authentication-related operations
 */
public class AuthViewModel extends ViewModel {
    private UserRepository userRepository;
    private FCMManager fcmManager;
    
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private MutableLiveData<Boolean> registrationSuccess = new MutableLiveData<>();
    private MutableLiveData<Boolean> loginSuccess = new MutableLiveData<>();
    private MutableLiveData<User> currentUser = new MutableLiveData<>();
    
    public AuthViewModel() {
        userRepository = UserRepository.getInstance();
        fcmManager = FCMManager.getInstance();
    }
    
    public void registerUser(String email, String password, String name, String userType) {
        isLoading.setValue(true);
        
        userRepository.registerUser(email, password, name, userType, new UserRepository.OnUserRegistrationListener() {
            @Override
            public void onSuccess(User user) {
                // Get FCM token and update in Firestore
                updateFCMToken();
                
                isLoading.setValue(false);
                registrationSuccess.setValue(true);
            }

            @Override
            public void onFailure(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
                registrationSuccess.setValue(false);
            }
        });
    }
    
    public void loginUser(String email, String password) {
        isLoading.setValue(true);
        
        userRepository.loginUser(email, password, new UserRepository.OnUserLoginListener() {
            @Override
            public void onSuccess(User user) {
                // Get FCM token and update in Firestore
                updateFCMToken();
                
                isLoading.setValue(false);
                loginSuccess.setValue(true);
            }

            @Override
            public void onFailure(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
                loginSuccess.setValue(false);
            }
        });
    }
    
    private void updateFCMToken() {
        fcmManager.getToken(task -> {
            if (task.isSuccessful()) {
                String token = task.getResult();
                userRepository.updateUserFCMToken(token);
            }
        });
    }
    
    public void logout() {
        userRepository.logoutUser();
    }
    
    public LiveData<User> getCurrentUser() {
        return currentUser;
    }
    
    public boolean isUserLoggedIn() {
        return userRepository.isUserLoggedIn();
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    public LiveData<Boolean> getRegistrationSuccess() {
        return registrationSuccess;
    }
    
    public LiveData<Boolean> getLoginSuccess() {
        return loginSuccess;
    }

    /**
     * Gets the current Firebase user directly
     * @return The FirebaseUser or null if not logged in
     */
    public FirebaseUser getFirebaseUser() {
        return FirebaseAuth.getInstance().getCurrentUser();
    }

    /**
     * Gets the current user ID
     * @return The user ID or null if no user is logged in
     */
    public String getCurrentUserId() {
        FirebaseUser user = getFirebaseUser();
        return user != null ? user.getUid() : null;
    }

    /**
     * Refreshes the current user data from Firestore
     */
    public void refreshCurrentUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String uid = user.getUid();
            
            // Load user data from Firestore
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User userData = documentSnapshot.toObject(User.class);
                        if (userData != null) {
                            userData.setId(uid);
                            currentUser.setValue(userData);
                        }
                    }
                });
        }
    }

    /**
     * Check if the current user has a defined role and linked account
     * @param callback A callback that provides the role and linking status
     */
    public void checkUserRoleAndLinking(UserRoleCallback callback) {
        String userId = getCurrentUserId();
        
        if (userId == null) {
            callback.onResult(false, null, false);
            return;
        }
        
        FirebaseFirestore.getInstance().collection("users").document(userId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String userType = documentSnapshot.getString("userType");
                    String role = documentSnapshot.getString("role");
                    String linkedUserId = documentSnapshot.getString("linkedUserId");
                    
                    // Use role field if available, otherwise fallback to userType
                    String effectiveRole = role != null ? role : userType;
                    
                    boolean hasRole = effectiveRole != null && !effectiveRole.isEmpty();
                    boolean isLinked = linkedUserId != null && !linkedUserId.isEmpty();
                    
                    callback.onResult(hasRole, effectiveRole, isLinked);
                } else {
                    callback.onResult(false, null, false);
                }
            })
            .addOnFailureListener(e -> {
                callback.onResult(false, null, false);
            });
    }

    /**
     * Interface for user role check callback
     */
    public interface UserRoleCallback {
        /**
         * Called when the role check is complete
         * @param hasRole True if user has a role defined
         * @param role The user's role (parent/child) or null
         * @param isLinked True if user has a linkedUserId
         */
        void onResult(boolean hasRole, String role, boolean isLinked);
    }
}
