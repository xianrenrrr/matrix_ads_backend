package com.example.demo.dao;

import com.example.demo.model.BackgroundMusic;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Repository
public class BackgroundMusicDaoImpl implements BackgroundMusicDao {
    
    @Autowired(required = false)
    private Firestore db;
    
    private static final String COLLECTION = "background_music";
    
    @Override
    public String saveBackgroundMusic(BackgroundMusic bgm) throws ExecutionException, InterruptedException {
        if (db == null) throw new IllegalStateException("Firestore not initialized");
        
        if (bgm.getId() == null || bgm.getId().isEmpty()) {
            DocumentReference docRef = db.collection(COLLECTION).document();
            bgm.setId(docRef.getId());
        }
        
        db.collection(COLLECTION).document(bgm.getId()).set(bgm).get();
        return bgm.getId();
    }
    
    @Override
    public BackgroundMusic getBackgroundMusic(String id) throws ExecutionException, InterruptedException {
        if (db == null) return null;
        
        DocumentSnapshot doc = db.collection(COLLECTION).document(id).get().get();
        if (!doc.exists()) return null;
        
        return doc.toObject(BackgroundMusic.class);
    }
    
    @Override
    public List<BackgroundMusic> getBackgroundMusicByUserId(String userId) throws ExecutionException, InterruptedException {
        if (db == null) return new ArrayList<>();
        
        QuerySnapshot querySnapshot = db.collection(COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("uploadedAt", Query.Direction.DESCENDING)
            .get()
            .get();
        
        List<BackgroundMusic> bgmList = new ArrayList<>();
        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
            bgmList.add(doc.toObject(BackgroundMusic.class));
        }
        return bgmList;
    }
    
    @Override
    public boolean deleteBackgroundMusic(String id) throws ExecutionException, InterruptedException {
        if (db == null) return false;
        
        db.collection(COLLECTION).document(id).delete().get();
        return true;
    }
}
