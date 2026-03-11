package com.myapp.guidegrowth.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.FirebaseFirestore;
import com.myapp.guidegrowth.model.Child;

import java.util.ArrayList;
import java.util.List;

public class ChildViewModel extends ViewModel {

    private final MutableLiveData<List<Child>> childrenList = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    
    public LiveData<List<Child>> getChildrenList() {
        return childrenList;
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    public void loadChildren(List<String> childIds) {
        if (childIds == null || childIds.isEmpty()) {
            childrenList.setValue(new ArrayList<>());
            return;
        }
        
        List<Child> children = new ArrayList<>();
        
        // Counter to track when all queries are completed
        final int[] completedQueries = {0};
        final int totalQueries = childIds.size();
        
        for (String childId : childIds) {
            // First try to get data from the children collection
            db.collection("children").document(childId)
                .get()
                .addOnSuccessListener(childSnapshot -> {
                    Child child = null;
                    
                    if (childSnapshot.exists()) {
                        // Create child from children collection data
                        String name = childSnapshot.getString("name");
                        String email = childSnapshot.getString("email");
                        
                        child = new Child(childId, name != null ? name : "Unknown Child", email);
                        child.setLastUpdated(childSnapshot.getLong("lastUpdated"));
                        
                        // Get other fields if they exist
                        if (childSnapshot.contains("status")) {
                            child.setStatus(childSnapshot.getString("status"));
                        }
                        
                        // Check if there's location data in the location subcollection
                        loadLocationData(child);
                        
                        // Add the child to our list
                        children.add(child);
                    }
                    
                    // If no child found in children collection, try the users collection as fallback
                    if (child == null) {
                        db.collection("users").document(childId)
                            .get()
                            .addOnSuccessListener(userSnapshot -> {
                                if (userSnapshot.exists()) {
                                    Child userChild = Child.fromDocument(userSnapshot);
                                    if (userChild != null) {
                                        // Check if there's location data in the location subcollection
                                        loadLocationData(userChild);
                                        children.add(userChild);
                                    }
                                }
                                
                                // Increment counter and check if all queries are completed
                                completedQueries[0]++;
                                if (completedQueries[0] >= totalQueries) {
                                    childrenList.setValue(children);
                                }
                            })
                            .addOnFailureListener(e -> {
                                // Handle failure for user snapshot
                                completedQueries[0]++;
                                if (completedQueries[0] >= totalQueries) {
                                    childrenList.setValue(children);
                                }
                            });
                    } else {
                        // Increment counter if we already found the child
                        completedQueries[0]++;
                        if (completedQueries[0] >= totalQueries) {
                            childrenList.setValue(children);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    // Handle failure for child snapshot - try users collection as fallback
                    db.collection("users").document(childId)
                        .get()
                        .addOnSuccessListener(userSnapshot -> {
                            if (userSnapshot.exists()) {
                                Child userChild = Child.fromDocument(userSnapshot);
                                if (userChild != null) {
                                    // Check if there's location data in the location subcollection
                                    loadLocationData(userChild);
                                    children.add(userChild);
                                }
                            }
                            
                            // Always increment counter
                            completedQueries[0]++;
                            if (completedQueries[0] >= totalQueries) {
                                childrenList.setValue(children);
                            }
                        })
                        .addOnFailureListener(userError -> {
                            errorMessage.setValue("Error loading child data: " + userError.getMessage());
                            
                            // Increment counter on failure
                            completedQueries[0]++;
                            if (completedQueries[0] >= totalQueries) {
                                childrenList.setValue(children);
                            }
                        });
                });
        }
    }
    
    private void loadLocationData(Child child) {
        if (child == null || child.getId() == null) return;
        
        // Check for location data in the location subcollection
        db.collection("children").document(child.getId())
            .collection("location").document("current")
            .get()
            .addOnSuccessListener(locationSnapshot -> {
                if (locationSnapshot.exists()) {
                    Double latitude = locationSnapshot.getDouble("latitude");
                    Double longitude = locationSnapshot.getDouble("longitude");
                    Long timestamp = locationSnapshot.getLong("timestamp");
                    
                    if (latitude != null && longitude != null) {
                        child.setLatitude(latitude);
                        child.setLongitude(longitude);
                        
                        if (timestamp != null) {
                            child.setLastLocationUpdate(timestamp);
                        } else {
                            // Use current time if no timestamp
                            child.setLastLocationUpdate(System.currentTimeMillis());
                        }
                        
                        // Notify observers of data change
                        List<Child> currentList = childrenList.getValue();
                        if (currentList != null) {
                            childrenList.setValue(currentList);
                        }
                    }
                }
            });
    }
}
