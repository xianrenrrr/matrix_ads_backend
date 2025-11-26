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
    
    @Autowired(required = false)
    private com.example.demo.service.VideoTranscodingService videoTranscodingService;
    
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
            
            // Check if video needs transcoding for WeChat compatibility
            java.io.File tempInputFile = null;
            java.io.File transcodedFile = null;
            org.springframework.web.multipart.MultipartFile fileToUpload = file;
            
            if (videoTranscodingService != null) {
                try {
                    // Save to temp file for codec check
                    tempInputFile = java.io.File.createTempFile("video_check_", ".mp4");
                    file.transferTo(tempInputFile);
                    
                    if (videoTranscodingService.needsTranscoding(tempInputFile)) {
                        System.out.println("[VIDEO-UPLOAD] Video needs transcoding for WeChat compatibility");
                        transcodedFile = videoTranscodingService.transcodeIfNeeded(tempInputFile);
                        
                        if (transcodedFile != tempInputFile) {
                            // Transcoding succeeded, use transcoded file
                            System.out.println("[VIDEO-UPLOAD] Using transcoded video: " + transcodedFile.length() + " bytes");
                            // Create a new MultipartFile from the transcoded file
                            final java.io.File finalTranscodedFile = transcodedFile;
                            fileToUpload = new org.springframework.web.multipart.MultipartFile() {
                                @Override public String getName() { return "file"; }
                                @Override public String getOriginalFilename() { return file.getOriginalFilename(); }
                                @Override public String getContentType() { return "video/mp4"; }
                                @Override public boolean isEmpty() { return finalTranscodedFile.length() == 0; }
                                @Override public long getSize() { return finalTranscodedFile.length(); }
                                @Override public byte[] getBytes() throws java.io.IOException {
                                    return java.nio.file.Files.readAllBytes(finalTranscodedFile.toPath());
                                }
                                @Override public java.io.InputStream getInputStream() throws java.io.IOException {
                                    return new java.io.FileInputStream(finalTranscodedFile);
                                }
                                @Override public void transferTo(java.io.File dest) throws java.io.IOException {
                                    java.nio.file.Files.copy(finalTranscodedFile.toPath(), dest.toPath(), 
                                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                }
                            };
                        }
                    } else {
                        System.out.println("[VIDEO-UPLOAD] Video codec is WeChat compatible, no transcoding needed");
                    }
                } catch (Exception e) {
                    System.err.println("[VIDEO-UPLOAD] Transcoding check failed, using original: " + e.getMessage());
                }
            }
            
            com.example.demo.service.AlibabaOssStorageService.UploadResult uploadResult = 
                ossStorageService.uploadVideoWithThumbnail(fileToUpload, userId, videoId);
            System.out.println("[VIDEO-UPLOAD] ✅ Upload complete: " + uploadResult.videoUrl);
            
            // Clean up temp files
            if (tempInputFile != null && tempInputFile.exists()) {
                tempInputFile.delete();
            }
            if (transcodedFile != null && transcodedFile != tempInputFile && transcodedFile.exists()) {
                transcodedFile.delete();
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
