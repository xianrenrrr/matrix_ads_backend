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
    
    @Autowired(required = false)
    private com.example.demo.service.AlibabaOssStorageService ossStorageService;
    
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
    
    @Override
    public BackgroundMusic uploadAndSaveBackgroundMusic(org.springframework.web.multipart.MultipartFile file, String userId, String title, String description) throws Exception {
        if (ossStorageService == null) {
            throw new IllegalStateException("AlibabaOssStorageService not available");
        }
        
        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("audio/")) {
            throw new IllegalArgumentException("File must be an audio file");
        }
        
        // Generate BGM ID
        String bgmId = java.util.UUID.randomUUID().toString();
        
        // Extract audio duration using FFprobe
        long durationSeconds = extractAudioDuration(file);
        
        // Upload to OSS
        String objectName = String.format("bgm/%s/%s/%s", userId, bgmId, file.getOriginalFilename());
        java.io.File tempFile = java.io.File.createTempFile("bgm-upload-", file.getOriginalFilename());
        try {
            file.transferTo(tempFile);
            String audioUrl = ossStorageService.uploadFile(tempFile, objectName, file.getContentType());
            
            // Create BGM record
            BackgroundMusic bgm = new BackgroundMusic();
            bgm.setId(bgmId);
            bgm.setUserId(userId);
            bgm.setTitle(title != null && !title.isBlank() ? title : file.getOriginalFilename());
            bgm.setDescription(description);
            bgm.setAudioUrl(audioUrl);
            bgm.setDurationSeconds(durationSeconds);
            bgm.setUploadedAt(java.time.LocalDateTime.now().toString());
            
            saveBackgroundMusic(bgm);
            return bgm;
        } finally {
            tempFile.delete();
        }
    }
    
    private long extractAudioDuration(org.springframework.web.multipart.MultipartFile file) throws Exception {
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
}
