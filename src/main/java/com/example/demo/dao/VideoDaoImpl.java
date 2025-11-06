package com.example.demo.dao;

import com.example.demo.model.Video;
import com.example.demo.model.ManualTemplate;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;



import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Repository
public class VideoDaoImpl implements VideoDao {
    @Autowired(required = false)
    private Firestore db;
    
    @Autowired(required = false)
    private com.example.demo.service.AlibabaOssStorageService ossStorageService;
    
    private void checkFirestore() {
        if (db == null) {
            throw new IllegalStateException("Firestore is not available in development mode. Please configure Firebase credentials or use a different data source.");
        }
    }

    @Override
    public Video saveVideo(Video video) throws ExecutionException, InterruptedException {
        // Only generate ID if not already set
        String videoId = video.getId();
        if (videoId == null || videoId.isEmpty()) {
            videoId = UUID.randomUUID().toString();
            video.setId(videoId);
        }
        
        // Store in 'exampleVideos' collection instead of 'videos'
        DocumentReference docRef = db.collection("exampleVideos").document(videoId);
        ApiFuture<WriteResult> result = docRef.set(video);
        result.get(); // Wait for write to complete
        return video;
    }


    @Override
    public Video getVideoById(String videoId) throws ExecutionException, InterruptedException {
        // First try exampleVideos collection
        DocumentReference docRef = db.collection("exampleVideos").document(videoId);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            return document.toObject(Video.class);
        }
        
        // If not found, try submittedVideos collection
        DocumentReference submittedDocRef = db.collection("submittedVideos").document(videoId);
        ApiFuture<DocumentSnapshot> submittedFuture = submittedDocRef.get();
        DocumentSnapshot submittedDocument = submittedFuture.get();
        if (submittedDocument.exists()) {
            return submittedDocument.toObject(Video.class);
        }
        
        return null;
    }

    @Override
    public void updateVideo(Video video) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection("exampleVideos").document(video.getId());
        ApiFuture<WriteResult> result = docRef.set(video);
        result.get(); // Wait for write to complete
    }

    @Override
    public Video saveVideoWithTemplate(Video video, String templateId) throws ExecutionException, InterruptedException {
        // First save the video
        String videoId = UUID.randomUUID().toString();
        video.setId(videoId);
        
        video.setTemplateId(templateId);
        
        DocumentReference docRef = db.collection("exampleVideos").document(videoId);
        ApiFuture<WriteResult> result = docRef.set(video);
        result.get(); // Wait for write to complete
        
        // If template exists, update it with video ID
        if (templateId != null && !templateId.isEmpty()) {
            DocumentReference templateRef = db.collection("templates").document(templateId);
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

    @Override
    public boolean deleteVideoById(String videoId) throws ExecutionException, InterruptedException {
        checkFirestore();
        try {
            DocumentReference docRef = db.collection("exampleVideos").document(videoId);
            ApiFuture<WriteResult> write = docRef.delete();
            write.get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public Video uploadAndSaveVideo(org.springframework.web.multipart.MultipartFile file, String userId, String videoId) throws Exception {
        if (ossStorageService == null) {
            throw new IllegalStateException("AlibabaOssStorageService not available");
        }
        
        // First, upload the video (this will handle the file properly)
        System.out.println("[VIDEO-UPLOAD] Starting upload for videoId: " + videoId);
        com.example.demo.service.AlibabaOssStorageService.UploadResult uploadResult = 
            ossStorageService.uploadVideoWithThumbnail(file, userId, videoId);
        System.out.println("[VIDEO-UPLOAD] Upload complete: " + uploadResult.videoUrl);
        
        // Now extract duration from the uploaded video using a signed URL
        long durationSeconds = 0;
        java.io.File tempFile = null;
        try {
            // Download video temporarily for duration extraction
            String signedUrl = ossStorageService.generateSignedUrl(uploadResult.videoUrl, 5, java.util.concurrent.TimeUnit.MINUTES);
            tempFile = java.io.File.createTempFile("video_duration_", ".mp4");
            
            // Download from OSS
            java.net.URL url = new java.net.URL(signedUrl);
            try (java.io.InputStream in = url.openStream()) {
                java.nio.file.Files.copy(in, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Use FFprobe to get duration
            ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", tempFile.getAbsolutePath()
            );
            Process process = pb.start();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream())
            );
            String durationStr = reader.readLine();
            process.waitFor();
            
            if (durationStr != null && !durationStr.isEmpty()) {
                durationSeconds = (long) Double.parseDouble(durationStr);
                System.out.println("[VIDEO-DURATION] Extracted duration: " + durationSeconds + " seconds");
            }
        } catch (Exception e) {
            System.err.println("[VIDEO-DURATION] Failed to extract duration: " + e.getMessage());
            e.printStackTrace();
            // Continue without duration
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
        
        // Create and save video object
        Video video = new Video();
        video.setId(videoId);
        video.setUserId(userId);
        video.setUrl(uploadResult.videoUrl);
        video.setThumbnailUrl(uploadResult.thumbnailUrl);
        video.setDurationSeconds(durationSeconds);
        
        return saveVideo(video);
    }
    
    @Override
    public String getSignedUrl(String videoUrl) throws Exception {
        if (ossStorageService == null || videoUrl == null) {
            return videoUrl;
        }
        return ossStorageService.generateSignedUrl(videoUrl);
    }
}
