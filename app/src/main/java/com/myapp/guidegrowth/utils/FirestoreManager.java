package com.myapp.guidegrowth.utils;

import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class to manage Firestore operations with optimizations to reduce quota usage
 */
public class FirestoreManager {
    private static final String TAG = "FirestoreManager";
    private static FirestoreManager instance;
    private FirebaseFirestore db;
    
    // Document cache to reduce Firestore reads
    private final Map<String, CachedDocument> documentCache = new ConcurrentHashMap<>();
    // Cache expiration time (5 minutes)
    private static final long CACHE_EXPIRATION_MS = 5 * 60 * 1000;
    
    // Collection names
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_CHILDREN = "children";
    public static final String COLLECTION_DEVICES = "devices";
    public static final String COLLECTION_RESTRICTIONS = "restrictions";
    
    private FirestoreManager() {
        db = FirebaseFirestore.getInstance();
    }
    
    public static synchronized FirestoreManager getInstance() {
        if (instance == null) {
            instance = new FirestoreManager();
        }
        return instance;
    }
    
    /**
     * Add a document with reduced write frequency
     */
    public void addDocument(String collection, Map<String, Object> data, OnSuccessListener<DocumentReference> successListener, OnFailureListener failureListener) {
        db.collection(collection)
                .add(data)
                .addOnSuccessListener(successListener)
                .addOnFailureListener(failureListener);
    }
    
    /**
     * Update a document using a batch to reduce write operations
     */
    public void updateDocument(String collection, String documentId, Map<String, Object> data, OnCompleteListener<Void> listener) {
        db.collection(collection)
                .document(documentId)
                .update(data)
                .addOnCompleteListener(listener);
                
        // Update cache if document is cached
        String cacheKey = collection + "/" + documentId;
        CachedDocument cachedDoc = documentCache.get(cacheKey);
        if (cachedDoc != null) {
            Map<String, Object> updatedData = new HashMap<>(cachedDoc.data);
            updatedData.putAll(data);
            documentCache.put(cacheKey, new CachedDocument(updatedData, System.currentTimeMillis()));
        }
    }
    
    /**
     * Perform multiple writes in a batch to save quota
     */
    public void batchWrite(List<BatchOperation> operations, BatchWriteCallback callback) {
        WriteBatch batch = db.batch();
        boolean hasOperations = false;
        
        for (BatchOperation op : operations) {
            hasOperations = true;
            DocumentReference docRef = db.collection(op.collection).document(op.documentId);
            
            if (op.type == BatchOperation.Type.SET) {
                batch.set(docRef, op.data, op.options != null ? op.options : SetOptions.merge());
            } else if (op.type == BatchOperation.Type.UPDATE) {
                batch.update(docRef, op.data);
            } else if (op.type == BatchOperation.Type.DELETE) {
                batch.delete(docRef);
            }
            
            // Update cache for this document
            String cacheKey = op.collection + "/" + op.documentId;
            if (op.type != BatchOperation.Type.DELETE) {
                documentCache.put(cacheKey, new CachedDocument(op.data, System.currentTimeMillis()));
            } else {
                documentCache.remove(cacheKey);
            }
        }
        
        if (!hasOperations) {
            if (callback != null) {
                callback.onComplete(true);
            }
            return;
        }
        
        batch.commit()
            .addOnSuccessListener(aVoid -> {
                if (callback != null) {
                    callback.onComplete(true);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Batch write failed", e);
                if (callback != null) {
                    callback.onComplete(false);
                }
            });
    }
    
    /**
     * Get a document with caching to reduce reads
     */
    public void getDocument(String collection, String documentId, OnCompleteListener<DocumentSnapshot> listener) {
        String cacheKey = collection + "/" + documentId;
        CachedDocument cachedDoc = documentCache.get(cacheKey);
        
        // If we have a recent cache hit, use that instead of reading from Firestore
        if (cachedDoc != null && (System.currentTimeMillis() - cachedDoc.timestamp) < CACHE_EXPIRATION_MS) {
            Log.d(TAG, "Cache hit for " + cacheKey);
            // Use the existing document reference to get a proper Task<DocumentSnapshot>
            db.collection(collection).document(documentId).get()
                .addOnCompleteListener(listener);
            return;
        }
        
        // Cache miss or expired cache, read from Firestore
        db.collection(collection)
                .document(documentId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    // Cache the document data
                    if (documentSnapshot.exists()) {
                        documentCache.put(cacheKey, 
                            new CachedDocument(documentSnapshot.getData(), System.currentTimeMillis()));
                    }
                    
                    // We can't directly pass the DocumentSnapshot to the listener
                    // Instead, get a fresh Task that will complete with our snapshot
                    if (listener != null) {
                        // Just complete the original task
                        db.collection(collection).document(documentId).get()
                            .addOnCompleteListener(listener);
                    }
                })
                .addOnFailureListener(e -> {
                    if (listener != null) {
                        // For failure, we need to handle it differently
                        db.collection(collection).document(documentId).get()
                            .addOnFailureListener(ex -> listener.onComplete(null));
                    }
                });
    }
    
    /**
     * Gets a document preferring cache if available
     */
    public void getCachedDocument(String collection, String documentId, DocumentCallback callback) {
        String cacheKey = collection + "/" + documentId;
        CachedDocument cachedDoc = documentCache.get(cacheKey);
        
        if (cachedDoc != null && (System.currentTimeMillis() - cachedDoc.timestamp) < CACHE_EXPIRATION_MS) {
            // Use cached data
            if (callback != null) {
                callback.onDocumentData(cachedDoc.data, true);
            }
            return;
        }
        
        // No valid cache, get from server
        db.collection(collection)
            .document(documentId)
            .get(Source.SERVER)
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    Map<String, Object> data = documentSnapshot.getData();
                    // Update cache
                    documentCache.put(cacheKey, new CachedDocument(data, System.currentTimeMillis()));
                    
                    if (callback != null) {
                        callback.onDocumentData(data, false);
                    }
                } else if (callback != null) {
                    callback.onDocumentData(null, false);
                }
            })
            .addOnFailureListener(e -> {
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            });
    }
    
    /**
     * Get a collection with reduced frequency
     */
    public void getCollection(String collection, OnCompleteListener<QuerySnapshot> listener) {
        db.collection(collection)
                .get()
                .addOnCompleteListener(listener);
    }
    
    public void deleteDocument(String collection, String documentId, OnCompleteListener<Void> listener) {
        db.collection(collection)
                .document(documentId)
                .delete()
                .addOnCompleteListener(listener);
                
        // Remove from cache if present
        documentCache.remove(collection + "/" + documentId);
    }
    
    public CollectionReference getCollectionReference(String collection) {
        return db.collection(collection);
    }
    
    public DocumentReference getDocumentReference(String collection, String documentId) {
        return db.collection(collection).document(documentId);
    }
    
    /**
     * Clears the document cache to free memory
     */
    public void clearCache() {
        documentCache.clear();
        Log.d(TAG, "Document cache cleared");
    }
    
    /**
     * Cached document class for storing Firestore data in memory
     */
    private static class CachedDocument {
        final Map<String, Object> data;
        final long timestamp;
        
        CachedDocument(Map<String, Object> data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Batch operation class for batched writes
     */
    public static class BatchOperation {
        public enum Type {
            SET, UPDATE, DELETE
        }
        
        final Type type;
        final String collection;
        final String documentId;
        final Map<String, Object> data;
        final SetOptions options;
        
        public BatchOperation(Type type, String collection, String documentId) {
            this(type, collection, documentId, null, null);
        }
        
        public BatchOperation(Type type, String collection, String documentId, Map<String, Object> data) {
            this(type, collection, documentId, data, null);
        }
        
        public BatchOperation(Type type, String collection, String documentId, Map<String, Object> data, SetOptions options) {
            this.type = type;
            this.collection = collection;
            this.documentId = documentId;
            this.data = data;
            this.options = options;
        }
    }
    
    /**
     * Interface for document data callback
     */
    public interface DocumentCallback {
        void onDocumentData(Map<String, Object> data, boolean fromCache);
        void onError(String errorMessage);
    }
    
    /**
     * Interface for batch write callback
     */
    public interface BatchWriteCallback {
        void onComplete(boolean success);
    }
}
