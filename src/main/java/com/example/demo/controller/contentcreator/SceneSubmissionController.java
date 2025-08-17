package com.example.demo.controller.contentcreator;

import com.example.demo.dao.SceneSubmissionDao;
import com.example.demo.dao.TemplateDao;
import com.example.demo.model.SceneSubmission;
import com.example.demo.model.ManualTemplate;
import com.example.demo.service.FirebaseStorageService;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/content-creator/scenes")
public class SceneSubmissionController {
    
    @Autowired
    private SceneSubmissionDao sceneSubmissionDao;
    
    @Autowired
    private TemplateDao templateDao;
    
    @Autowired
    private FirebaseStorageService firebaseStorageService;
    
    @Autowired
    private Firestore db;
    
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadScene(
            @RequestParam("file") MultipartFile file,
            @RequestParam("templateId") String templateId,
            @RequestParam("userId") String userId,
            @RequestParam("sceneNumber") int sceneNumber,
            @RequestParam(value = "sceneTitle", required = false) String sceneTitle) throws Exception {
        
        if (file.isEmpty()) throw new IllegalArgumentException("File is empty");
        
        ManualTemplate template = templateDao.getTemplate(templateId);
        if (template == null) throw new NoSuchElementException("Template not found");
        
        if (sceneNumber < 1 || sceneNumber > template.getScenes().size()) {
            throw new IllegalArgumentException("Invalid scene number");
        }
        
        com.example.demo.model.Scene templateScene = template.getScenes().get(sceneNumber - 1);
        String compositeVideoId = userId + "_" + templateId;
        
        // Upload file
        String sceneVideoId = UUID.randomUUID().toString();
        FirebaseStorageService.UploadResult uploadResult = firebaseStorageService.uploadVideoWithThumbnail(file, userId, sceneVideoId);
        
        // Create scene submission
        SceneSubmission sceneSubmission = new SceneSubmission(templateId, userId, sceneNumber, 
            sceneTitle != null ? sceneTitle : templateScene.getSceneTitle());
        
        sceneSubmission.setVideoUrl(uploadResult.videoUrl);
        sceneSubmission.setThumbnailUrl(uploadResult.thumbnailUrl);
        sceneSubmission.setOriginalFileName(file.getOriginalFilename());
        sceneSubmission.setFileSize(file.getSize());
        sceneSubmission.setFormat(getFileExtension(file.getOriginalFilename()));
        
        // Basic mock AI scores - NO GOOGLE API CALLS
        sceneSubmission.setSimilarityScore(0.85); // Mock score
        sceneSubmission.setAiSuggestions(Arrays.asList("Good quality", "Well framed"));
        
        String sceneId = sceneSubmissionDao.save(sceneSubmission);
        sceneSubmission.setId(sceneId);
        
        updateSubmittedVideoWithScene(compositeVideoId, templateId, userId, sceneSubmission);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Scene uploaded successfully");
        response.put("sceneSubmission", sceneSubmission);
        
        return ResponseEntity.ok(response);
    }
    @GetMapping("/submitted-videos/{compositeVideoId}")
    public ResponseEntity<Map<String, Object>> getSubmittedVideo(@PathVariable String compositeVideoId) throws Exception {
        DocumentSnapshot videoDoc = db.collection("submittedVideos").document(compositeVideoId).get().get();
        
        if (!videoDoc.exists()) {
            throw new NoSuchElementException("No submission found");
        }
        
        Map<String, Object> videoData = new HashMap<>(videoDoc.getData());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> scenes = (Map<String, Object>) videoData.get("scenes");
        if (scenes != null) {
            Map<String, Object> fullScenes = new HashMap<>();
            
            for (Map.Entry<String, Object> entry : scenes.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> sceneRef = (Map<String, Object>) entry.getValue();
                String sceneId = (String) sceneRef.get("sceneId");
                
                if (sceneId != null) {
                    SceneSubmission sceneSubmission = sceneSubmissionDao.findById(sceneId);
                    if (sceneSubmission != null) {
                        Map<String, Object> fullSceneData = new HashMap<>();
                        fullSceneData.put("sceneId", sceneSubmission.getId());
                        fullSceneData.put("sceneNumber", sceneSubmission.getSceneNumber());
                        fullSceneData.put("sceneTitle", sceneSubmission.getSceneTitle());
                        fullSceneData.put("videoUrl", sceneSubmission.getVideoUrl());
                        fullSceneData.put("thumbnailUrl", sceneSubmission.getThumbnailUrl());
                        fullSceneData.put("status", sceneSubmission.getStatus());
                        fullSceneData.put("similarityScore", sceneSubmission.getSimilarityScore());
                        fullSceneData.put("aiSuggestions", sceneSubmission.getAiSuggestions());
                        fullSceneData.put("submittedAt", sceneSubmission.getSubmittedAt());
                        fullScenes.put(entry.getKey(), fullSceneData);
                    } else {
                        fullScenes.put(entry.getKey(), sceneRef);
                    }
                }
            }
            videoData.put("scenes", fullScenes);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.putAll(videoData);
        
        return ResponseEntity.ok(response);
    }

    @SuppressWarnings("unchecked")
    private void updateSubmittedVideoWithScene(String compositeVideoId, String templateId, String userId, SceneSubmission sceneSubmission) throws Exception {
        DocumentReference videoDocRef = db.collection("submittedVideos").document(compositeVideoId);
        DocumentSnapshot videoDoc = videoDocRef.get().get();
        
        Map<String, Object> sceneData = new HashMap<>();
        sceneData.put("sceneId", sceneSubmission.getId());
        sceneData.put("status", sceneSubmission.getStatus());
        
        if (videoDoc.exists()) {
            Map<String, Object> currentScenes = (Map<String, Object>) videoDoc.get("scenes");
            if (currentScenes == null) currentScenes = new HashMap<>();
            
            currentScenes.put(String.valueOf(sceneSubmission.getSceneNumber()), sceneData);
            
            int templateTotalScenes = getTemplateTotalScenes(templateId);
            int approvedScenes = 0;
            int pendingScenes = 0;
            
            for (Object sceneObj : currentScenes.values()) {
                if (sceneObj instanceof Map) {
                    String status = (String) ((Map<String, Object>) sceneObj).get("status");
                    if ("approved".equals(status)) approvedScenes++;
                    else if ("pending".equals(status)) pendingScenes++;
                }
            }
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("scenes", currentScenes);
            updates.put("lastUpdated", FieldValue.serverTimestamp());
            updates.put("progress", Map.of(
                "totalScenes", templateTotalScenes,
                "approved", approvedScenes,
                "pending", pendingScenes,
                "completionPercentage", templateTotalScenes > 0 ? (double) approvedScenes / templateTotalScenes * 100 : 0
            ));
            
            videoDocRef.update(updates);
        } else {
            Map<String, Object> videoData = new HashMap<>();
            videoData.put("videoId", compositeVideoId);
            videoData.put("templateId", templateId);
            videoData.put("uploadedBy", userId);
            videoData.put("publishStatus", "pending");
            videoData.put("createdAt", FieldValue.serverTimestamp());
            videoData.put("lastUpdated", FieldValue.serverTimestamp());
            
            Map<String, Object> scenes = new HashMap<>();
            scenes.put(String.valueOf(sceneSubmission.getSceneNumber()), sceneData);
            videoData.put("scenes", scenes);
            
            int templateTotalScenes = getTemplateTotalScenes(templateId);
            videoData.put("progress", Map.of(
                "totalScenes", templateTotalScenes,
                "approved", 0,
                "pending", 1,
                "completionPercentage", 0.0
            ));
            
            videoDocRef.set(videoData);
            db.collection("templates").document(templateId).update("submittedVideos", FieldValue.arrayUnion(compositeVideoId));
        }
    }
    
    private String getFileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        }
        return "mp4";
    }
    
    private int getTemplateTotalScenes(String templateId) throws Exception {
        ManualTemplate template = templateDao.getTemplate(templateId);
        return (template != null && template.getScenes() != null) ? template.getScenes().size() : 0;
    }
}