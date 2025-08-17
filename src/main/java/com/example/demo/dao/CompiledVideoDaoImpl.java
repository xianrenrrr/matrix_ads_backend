package com.example.demo.dao;

import com.example.demo.model.CompiledVideo;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ExecutionException;

/**
 * Simple compiled video DAO implementation
 */
@Repository
public class CompiledVideoDaoImpl implements CompiledVideoDao {
    
    private static final String COLLECTION_NAME = "compiledVideos";
    
    @Autowired
    private Firestore db;
    
    @Override
    public String save(CompiledVideo compiledVideo) throws ExecutionException, InterruptedException {
        CollectionReference collection = db.collection(COLLECTION_NAME);
        
        if (compiledVideo.getId() == null) {
            DocumentReference docRef = collection.document();
            compiledVideo.setId(docRef.getId());
            docRef.set(compiledVideo);
            return compiledVideo.getId();
        } else {
            collection.document(compiledVideo.getId()).set(compiledVideo);
            return compiledVideo.getId();
        }
    }
    
    @Override
    public CompiledVideo findById(String id) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(id);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        
        if (document.exists()) {
            CompiledVideo video = document.toObject(CompiledVideo.class);
            video.setId(document.getId());
            return video;
        }
        return null;
    }
    
    @Override
    public CompiledVideo findByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                .whereEqualTo("templateId", templateId)
                .whereEqualTo("userId", userId)
                .limit(1);
        
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        for (QueryDocumentSnapshot document : querySnapshot.get().getDocuments()) {
            CompiledVideo video = document.toObject(CompiledVideo.class);
            video.setId(document.getId());
            return video;
        }
        return null;
    }
    
    @Override
    public int getCompletedVideoCountByUser(String userId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", "completed");
        return query.get().get().size();
    }
    
    @Override
    public int getPublishedVideoCountByUser(String userId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", "published");
        return query.get().get().size();
    }
}