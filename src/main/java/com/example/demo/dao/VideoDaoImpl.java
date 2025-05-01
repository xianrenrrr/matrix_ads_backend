package com.example.demo.dao;

import com.example.demo.model.Video;
import com.example.demo.model.ManualTemplate;
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

    @Override
    public void updateVideo(Video video) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection("videos").document(video.getId());
        ApiFuture<WriteResult> result = docRef.set(video);
        result.get(); // Wait for write to complete
    }

    @Override
    public Video saveVideoWithTemplate(Video video, String templateId) throws ExecutionException, InterruptedException {
        // First save the video
        String videoId = UUID.randomUUID().toString();
        video.setId(videoId);
        video.setCreatedAt(Instant.now());
        video.setTemplateId(templateId);
        
        DocumentReference docRef = db.collection("videos").document(videoId);
        ApiFuture<WriteResult> result = docRef.set(video);
        result.get(); // Wait for write to complete
        
        // If template exists, update it with video ID
        if (templateId != null && !templateId.isEmpty()) {
            DocumentReference templateRef = db.collection("video_template").document(templateId);
            ApiFuture<DocumentSnapshot> templateFuture = templateRef.get();
            DocumentSnapshot templateDocument = templateFuture.get();
            
            if (templateDocument.exists()) {
                ManualTemplate template = templateDocument.toObject(ManualTemplate.class);
                template.setVideoId(videoId);
                templateRef.set(template).get();
            }
        }
        
        return video;
    }
}
