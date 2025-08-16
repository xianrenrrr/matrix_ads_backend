package com.example.demo.dao;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Repository
public class SubmittedVideoDaoImpl implements SubmittedVideoDao {
    
    @Autowired
    private Firestore db;
    
    private static final String COLLECTION_NAME = "submittedVideos";
    
    @Override
    public Map<String, Object> getSubmittedVideo(String compositeVideoId) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(compositeVideoId);
        DocumentSnapshot doc = docRef.get().get();
        
        if (doc.exists()) {
            Map<String, Object> data = new HashMap<>(doc.getData());
            data.put("id", compositeVideoId);
            return data;
        }
        return null;
    }
    
    @Override
    public List<Map<String, Object>> getAllSubmittedVideos() throws ExecutionException, InterruptedException {
        CollectionReference collectionRef = db.collection(COLLECTION_NAME);
        ApiFuture<QuerySnapshot> querySnapshot = collectionRef.get();
        
        List<Map<String, Object>> videos = new ArrayList<>();
        for (DocumentSnapshot doc : querySnapshot.get().getDocuments()) {
            Map<String, Object> data = doc.getData();
            if (data != null) {
                data.put("id", doc.getId());
                videos.add(data);
            }
        }
        return videos;
    }
    
    @Override
    public List<Map<String, Object>> getSubmittedVideosByUser(String userId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME).whereEqualTo("uploadedBy", userId);
        QuerySnapshot snapshot = query.get().get();
        
        List<Map<String, Object>> videos = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Map<String, Object> data = doc.getData();
            if (data != null) {
                data.put("id", doc.getId());
                videos.add(data);
            }
        }
        return videos;
    }
    
    @Override
    public List<Map<String, Object>> getSubmittedVideosByStatus(String status) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME).whereEqualTo("publishStatus", status);
        QuerySnapshot snapshot = query.get().get();
        
        List<Map<String, Object>> videos = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Map<String, Object> data = doc.getData();
            if (data != null) {
                data.put("id", doc.getId());
                videos.add(data);
            }
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
        QuerySnapshot snapshot = query.get().get();
        return snapshot.size();
    }
    
    @Override
    public int getPublishedVideoCountByUser(String userId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
            .whereEqualTo("uploadedBy", userId)
            .whereEqualTo("feedback.publishStatus", "approved");
        QuerySnapshot snapshot = query.get().get();
        return snapshot.size();
    }
}