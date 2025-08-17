package com.example.demo.dao;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Repository
public class SubmittedVideoDaoImpl implements SubmittedVideoDao {
    
    private static final String COLLECTION_NAME = "submittedVideos";
    
    @Autowired
    private Firestore db;
    
    @Override
    public Map<String, Object> getSubmittedVideo(String compositeVideoId) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(compositeVideoId);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        
        if (document.exists()) {
            return document.getData();
        }
        return null;
    }
    
    @Override
    public List<Map<String, Object>> getAllSubmittedVideos() throws ExecutionException, InterruptedException {
        CollectionReference collection = db.collection(COLLECTION_NAME);
        ApiFuture<QuerySnapshot> querySnapshot = collection.get();
        
        List<Map<String, Object>> videos = new ArrayList<>();
        for (QueryDocumentSnapshot document : querySnapshot.get().getDocuments()) {
            Map<String, Object> videoData = new HashMap<>(document.getData());
            videoData.put("id", document.getId()); // Include document ID
            videos.add(videoData);
        }
        
        return videos;
    }
    
    @Override
    public List<Map<String, Object>> getSubmittedVideosByUser(String userId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME).whereEqualTo("uploadedBy", userId);
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        
        List<Map<String, Object>> videos = new ArrayList<>();
        for (QueryDocumentSnapshot document : querySnapshot.get().getDocuments()) {
            Map<String, Object> videoData = new HashMap<>(document.getData());
            videoData.put("id", document.getId());
            videos.add(videoData);
        }
        
        return videos;
    }
    
    @Override
    public List<Map<String, Object>> getSubmittedVideosByStatus(String status) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME).whereEqualTo("publishStatus", status);
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        
        List<Map<String, Object>> videos = new ArrayList<>();
        for (QueryDocumentSnapshot document : querySnapshot.get().getDocuments()) {
            Map<String, Object> videoData = new HashMap<>(document.getData());
            videoData.put("id", document.getId());
            videos.add(videoData);
        }
        
        return videos;
    }
    
    @Override
    public void updateSubmittedVideo(String compositeVideoId, Map<String, Object> updates) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(compositeVideoId);
        ApiFuture<WriteResult> result = docRef.update(updates);
        result.get(); // Wait for completion
    }
    
    @Override
    public int getVideoCountByUser(String userId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME).whereEqualTo("uploadedBy", userId);
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        return querySnapshot.get().size();
    }
    
    @Override
    public int getPublishedVideoCountByUser(String userId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                .whereEqualTo("uploadedBy", userId)
                .whereEqualTo("publishStatus", "published");
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        return querySnapshot.get().size();
    }
}