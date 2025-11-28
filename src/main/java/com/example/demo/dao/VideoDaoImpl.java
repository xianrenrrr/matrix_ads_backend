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
        
        try {
            // First, upload the video (this will handle the file properly)
            System.out.println("[VIDEO-UPLOAD] Starting upload for videoId: " + videoId);
            System.out.println("[VIDEO-UPLOAD] File: " + file.getOriginalFilename() + ", Size: " + file.getSize() + " bytes");
            
            // CRITICAL: Save multipart file to temp IMMEDIATELY using getInputStream()
            // This avoids Tomcat temp file cleanup issues on ephemeral filesystems (Render)
            java.io.File tempInputFile = java.io.File.createTempFile("video_upload_", ".mp4");
            try (java.io.InputStream inputStream = file.getInputStream();
                 java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempInputFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            System.out.println("[VIDEO-UPLOAD] Saved multipart to temp: " + tempInputFile.length() + " bytes");
            
            // Create a MultipartFile wrapper for the temp file
            // NOTE: FFmpeg transcoding removed to avoid OOM on Render (512MB limit)
            // Android/HarmonyOS mini-app uses wx.downloadFile() workaround for playback
            final java.io.File savedTempFile = tempInputFile;
            final String originalFilename = file.getOriginalFilename();
            final String contentType = file.getContentType();
            org.springframework.web.multipart.MultipartFile fileToUpload = new org.springframework.web.multipart.MultipartFile() {
                @Override public String getName() { return "file"; }
                @Override public String getOriginalFilename() { return originalFilename; }
                @Override public String getContentType() { return contentType != null ? contentType : "video/mp4"; }
                @Override public boolean isEmpty() { return savedTempFile.length() == 0; }
                @Override public long getSize() { return savedTempFile.length(); }
                @Override public byte[] getBytes() throws java.io.IOException {
                    return java.nio.file.Files.readAllBytes(savedTempFile.toPath());
                }
                @Override public java.io.InputStream getInputStream() throws java.io.IOException {
                    return new java.io.FileInputStream(savedTempFile);
                }
                @Override public void transferTo(java.io.File dest) throws java.io.IOException {
                    java.nio.file.Files.copy(savedTempFile.toPath(), dest.toPath(), 
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            };
            
            com.example.demo.service.AlibabaOssStorageService.UploadResult uploadResult = 
                ossStorageService.uploadVideoWithThumbnail(fileToUpload, userId, videoId);
            System.out.println("[VIDEO-UPLOAD] ✅ Upload complete: " + uploadResult.videoUrl);
            
            // Clean up temp file
            if (tempInputFile != null && tempInputFile.exists()) {
                tempInputFile.delete();
            }
            
            // Now extract duration from the uploaded video using centralized OSS download
            long durationSeconds = 0;
            java.io.File tempFile = null;
            try {
                System.out.println("[VIDEO-DURATION] Downloading video for duration extraction...");
                // Download video temporarily for duration extraction using centralized method
                tempFile = ossStorageService.downloadToTempFile(uploadResult.videoUrl, "video_duration_", ".mp4");
                
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
                int exitCode = process.waitFor();
                
                if (exitCode == 0 && durationStr != null && !durationStr.isEmpty()) {
                    durationSeconds = (long) Double.parseDouble(durationStr);
                    System.out.println("[VIDEO-DURATION] ✅ Extracted duration: " + durationSeconds + " seconds");
                } else {
                    System.err.println("[VIDEO-DURATION] ⚠️ FFprobe failed or returned empty (exit code: " + exitCode + ")");
                }
            } catch (Exception e) {
                System.err.println("[VIDEO-DURATION] ❌ Failed to extract duration: " + e.getMessage());
                e.printStackTrace();
                // Continue without duration
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    boolean deleted = tempFile.delete();
                    System.out.println("[VIDEO-DURATION] Temp file deleted: " + deleted);
                }
            }
            
            // Create and save video object
            Video video = new Video();
            video.setId(videoId);
            video.setUserId(userId);
            video.setUrl(uploadResult.videoUrl);
            video.setThumbnailUrl(uploadResult.thumbnailUrl);
            video.setDurationSeconds(durationSeconds);
            
            System.out.println("[VIDEO-UPLOAD] Saving video to Firestore...");
            Video savedVideo = saveVideo(video);
            System.out.println("[VIDEO-UPLOAD] ✅ Video saved successfully");
            
            return savedVideo;
            
        } catch (Exception e) {
            System.err.println("[VIDEO-UPLOAD] ❌ Upload failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    @Override
    public String getSignedUrl(String videoUrl) throws Exception {
        if (ossStorageService == null || videoUrl == null) {
            return videoUrl;
        }
        return ossStorageService.generateSignedUrl(videoUrl);
    }
}
