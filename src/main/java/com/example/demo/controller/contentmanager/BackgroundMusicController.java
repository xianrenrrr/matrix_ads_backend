package com.example.demo.controller.contentmanager;

import com.example.demo.api.ApiResponse;
import com.example.demo.dao.BackgroundMusicDao;
import com.example.demo.model.BackgroundMusic;
import com.example.demo.service.FirebaseStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/content-manager/bgm")
@CrossOrigin(origins = "*")
public class BackgroundMusicController {
    private static final Logger log = LoggerFactory.getLogger(BackgroundMusicController.class);
    
    @Autowired(required = false)
    private FirebaseStorageService firebaseStorageService;
    
    @Autowired
    private BackgroundMusicDao bgmDao;
    
    /**
     * Upload background music audio file
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<BackgroundMusic>> uploadBGM(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description) {
        
        try {
            log.info("Uploading BGM for user: {}", userId);
            
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("File is empty"));
            }
            
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("audio/")) {
                return ResponseEntity.badRequest().body(ApiResponse.error("File must be an audio file"));
            }
            
            // Generate BGM ID
            String bgmId = java.util.UUID.randomUUID().toString();
            
            // Extract audio duration using FFprobe
            long durationSeconds = extractAudioDuration(file);
            
            // Upload to GCS
            String audioUrl = uploadAudioToGCS(file, userId, bgmId);
            
            // Create BGM record
            BackgroundMusic bgm = new BackgroundMusic();
            bgm.setId(bgmId);
            bgm.setUserId(userId);
            bgm.setTitle(title != null && !title.isBlank() ? title : file.getOriginalFilename());
            bgm.setDescription(description);
            bgm.setAudioUrl(audioUrl);
            bgm.setDurationSeconds(durationSeconds);
            bgm.setUploadedAt(LocalDateTime.now().toString());
            
            bgmDao.saveBackgroundMusic(bgm);
            
            log.info("BGM uploaded successfully: {}", bgmId);
            return ResponseEntity.ok(ApiResponse.ok("BGM uploaded successfully", bgm));
            
        } catch (Exception e) {
            log.error("Error uploading BGM", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to upload BGM: " + e.getMessage()));
        }
    }
    
    /**
     * Get all BGM for a user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<BackgroundMusic>>> getUserBGM(@PathVariable String userId) {
        try {
            List<BackgroundMusic> bgmList = bgmDao.getBackgroundMusicByUserId(userId);
            return ResponseEntity.ok(ApiResponse.ok("BGM list retrieved", bgmList));
        } catch (Exception e) {
            log.error("Error retrieving BGM list", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to retrieve BGM list"));
        }
    }
    
    /**
     * Delete BGM
     */
    @DeleteMapping("/{bgmId}")
    public ResponseEntity<ApiResponse<Void>> deleteBGM(@PathVariable String bgmId) {
        try {
            bgmDao.deleteBackgroundMusic(bgmId);
            return ResponseEntity.ok(ApiResponse.ok("BGM deleted successfully", null));
        } catch (Exception e) {
            log.error("Error deleting BGM", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to delete BGM"));
        }
    }
    
    /**
     * Extract audio duration using FFprobe
     */
    private long extractAudioDuration(MultipartFile file) throws Exception {
        java.io.File tempAudio = java.io.File.createTempFile("bgm-", ".mp3");
        
        try {
            // Copy file to temp location
            try (java.io.InputStream is = file.getInputStream();
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(tempAudio)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            
            // Use FFprobe to get duration
            ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", tempAudio.getAbsolutePath()
            );
            
            Process proc = pb.start();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(proc.getInputStream())
            );
            String durationStr = reader.readLine();
            proc.waitFor();
            
            if (durationStr != null && !durationStr.isEmpty()) {
                return (long) Double.parseDouble(durationStr);
            }
            return 0;
            
        } finally {
            tempAudio.delete();
        }
    }
    
    /**
     * Upload audio file to GCS
     */
    private String uploadAudioToGCS(MultipartFile file, String userId, String bgmId) throws Exception {
        String objectName = String.format("bgm/%s/%s/%s", userId, bgmId, file.getOriginalFilename());
        
        com.google.cloud.storage.Storage storage = firebaseStorageService.getStorage();
        String bucketName = firebaseStorageService.getBucketName();
        
        com.google.cloud.storage.BlobInfo blobInfo = com.google.cloud.storage.BlobInfo.newBuilder(bucketName, objectName)
            .setContentType(file.getContentType())
            .build();
        
        try (java.io.InputStream is = file.getInputStream();
             com.google.cloud.storage.Storage.BlobWriteChannel writer = storage.writer(blobInfo)) {
            byte[] buffer = new byte[1024];
            int limit;
            while ((limit = is.read(buffer)) >= 0) {
                writer.write(java.nio.ByteBuffer.wrap(buffer, 0, limit));
            }
        }
        
        return String.format("https://storage.googleapis.com/%s/%s", bucketName, objectName);
    }
}
