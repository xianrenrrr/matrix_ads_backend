package com.example.demo.dao;

import com.example.demo.model.Video;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Repository
public class VideoDaoImpl implements VideoDao {
    @Autowired
    private Firestore db;

    @Override
    public Video saveVideo(Video video) throws ExecutionException, InterruptedException {
        String videoId = UUID.randomUUID().toString();
        video.setId(videoId);
        video.setCreatedAt(Instant.now());
        DocumentReference docRef = db.collection("videos").document(videoId);
        ApiFuture<WriteResult> result = docRef.set(video);
        result.get(); // Wait for write to complete
        return video;
    }

    @Override
    public List<Video> getVideosByUserId(String userId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = db.collection("videos").whereEqualTo("userId", userId).get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        List<Video> videos = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            Video video = document.toObject(Video.class);
            videos.add(video);
        }
        return videos;
    }

    @Override
    public Video getVideoById(String videoId) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection("videos").document(videoId);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            return document.toObject(Video.class);
        } else {
            return null;
        }
    }
}
