package com.example.demo.controller.contentcreator;

import com.example.demo.dao.SceneSubmissionDao;
import com.example.demo.dao.TemplateDao;
import com.example.demo.dao.VideoDao;
import com.example.demo.model.SceneSubmission;
import com.example.demo.api.ApiResponse;
import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Video;
// ComparisonAIService removed - using QwenSceneComparisonService
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/content-creator/scenes")
public class SceneSubmissionController {
    
    private static final Logger log = LoggerFactory.getLogger(SceneSubmissionController.class);
    
    @Autowired
    private SceneSubmissionDao sceneSubmissionDao;
    
    @Autowired
    private TemplateDao templateDao;
    
    @Autowired
    private Firestore db;
    
    // ComparisonAIService removed - using QwenSceneComparisonService
    
    @Autowired
    private com.example.demo.ai.services.QwenSceneComparisonService qwenComparisonService;  // New Qwen-based comparison
    
    @Autowired
    private VideoDao videoDao;
    
    @Autowired
    private com.example.demo.dao.TemplateAssignmentDao templateAssignmentDao;

    
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadScene(
            @RequestParam("file") MultipartFile file,
            @RequestParam("assignmentId") String assignmentId,
            @RequestParam("userId") String userId,
            @RequestParam("sceneNumber") int sceneNumber,
            @RequestParam(value = "sceneTitle", required = false) String sceneTitle) throws Exception {
        
        if (file.isEmpty()) throw new IllegalArgumentException("File is empty");
        
        ManualTemplate template = getTemplateByAssignmentId(assignmentId);
        if (template == null) throw new NoSuchElementException("Template not found");
        
        if (sceneNumber < 1 || sceneNumber > template.getScenes().size()) {
            throw new IllegalArgumentException("Invalid scene number");
        }
        
        com.example.demo.model.Scene templateScene = template.getScenes().get(sceneNumber - 1);
        String compositeVideoId = userId + "_" + assignmentId;
        
        // DAO handles upload and save
        String finalSceneTitle = sceneTitle != null ? sceneTitle : templateScene.getSceneTitle();
        SceneSubmission sceneSubmission = sceneSubmissionDao.uploadAndSaveScene(
            file, assignmentId, userId, sceneNumber, finalSceneTitle
        );
        
        updateSubmittedVideoWithScene(compositeVideoId, assignmentId, userId, sceneSubmission);
        
        // Process AI comparison asynchronously in background
        final String finalSceneId = sceneSubmission.getId();
        final String templateVideoUrl = getTemplateVideoUrl(template.getVideoId());
        final String userVideoUrl = sceneSubmission.getVideoUrl();
        
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting async AI comparison for scene {} using NEW direct 2-image method", sceneNumber);
                
                // NEW: Use direct 2-image comparison with purpose-driven evaluation
                com.example.demo.ai.services.ComparisonResult comparisonResult = qwenComparisonService.compareWithDirectVL(
                    templateScene, userVideoUrl, "zh");
                
                // Update the scene submission with AI results
                SceneSubmission updatedSubmission = sceneSubmissionDao.findById(finalSceneId);
                if (updatedSubmission != null) {
                    // Convert score from 0-100 to 0-1 for DB
                    updatedSubmission.setSimilarityScore(comparisonResult.getScore() / 100.0);
                    updatedSubmission.setAiSuggestions(comparisonResult.getSuggestions());
                    
                    // Auto-approval logic: per-group threshold (score is now 0-1 in DB)
                    String autoStatus = determineAutoStatus(userId, updatedSubmission.getSimilarityScore());
                    if (autoStatus != null) {
                        updatedSubmission.setStatus(autoStatus);
                    }
                    
                    sceneSubmissionDao.update(updatedSubmission);
                    
                    // Log the score
                    log.info("AI Comparison completed for scene {}: score={}/100 ({}%), suggestions={}",
                            sceneNumber, 
                            comparisonResult.getScore(),
                            String.format("%.1f", updatedSubmission.getSimilarityScore() * 100),
                            comparisonResult.getSuggestions());
                }
                
            } catch (Exception e) {
                log.error("Async AI comparison failed for scene {}: {}", sceneNumber, e.getMessage());
                // Update with fallback scores
                try {
                    SceneSubmission fallbackSubmission = sceneSubmissionDao.findById(finalSceneId);
                    if (fallbackSubmission != null) {
                        fallbackSubmission.setSimilarityScore(0.75);
                        fallbackSubmission.setAiSuggestions(Arrays.asList("AI分析暂时不可用", "请检查视频质量"));
                        sceneSubmissionDao.update(fallbackSubmission);
                    }
                } catch (Exception updateError) {
                    log.error("Failed to update with fallback scores: {}", updateError.getMessage());
                }
            }
        });
        
        // Flatten response data for mini app compatibility
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("sceneSubmission", sceneSubmission);
        // Add flattened fields that mini app expects directly
        responseData.put("similarityScore", sceneSubmission.getSimilarityScore());
        responseData.put("aiSuggestions", sceneSubmission.getAiSuggestions());
        responseData.put("status", sceneSubmission.getStatus());
        responseData.put("sceneId", sceneSubmission.getId());
        
        return ResponseEntity.ok(ApiResponse.ok("Scene uploaded successfully", responseData));
    }
    @GetMapping("/submitted-videos/{compositeVideoId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSubmittedVideo(@PathVariable String compositeVideoId) throws Exception {
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
                        // Attach signed URL for playback
                        try {
                            String signed = sceneSubmissionDao.getSignedUrl(sceneSubmission.getVideoUrl());
                            fullSceneData.put("videoSignedUrl", signed);
                        } catch (Exception ignored) {}
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
        
        // If compiledVideoUrl exists, attach a signed URL for client download
        try {
            Object compiledUrl = videoData.get("compiledVideoUrl");
            if (compiledUrl instanceof String) {
                String signed = sceneSubmissionDao.getSignedUrl((String) compiledUrl);
                videoData.put("compiledVideoSignedUrl", signed);
            }
        } catch (Exception ignored) {}

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.putAll(videoData);
        
        return ResponseEntity.ok(ApiResponse.ok("Submitted video retrieved successfully", response));
    }

    /**
     * Get single scene submission by ID with playback URLs
     */
    @GetMapping("/{sceneId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSceneById(@PathVariable String sceneId) throws Exception {
        SceneSubmission scene = sceneSubmissionDao.findById(sceneId);
        if (scene == null) {
            throw new NoSuchElementException("Scene not found");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("sceneId", scene.getId());
        data.put("sceneNumber", scene.getSceneNumber());
        data.put("sceneTitle", scene.getSceneTitle());
        data.put("videoUrl", scene.getVideoUrl());
        data.put("thumbnailUrl", scene.getThumbnailUrl());
        data.put("status", scene.getStatus());
        data.put("similarityScore", scene.getSimilarityScore());
        data.put("aiSuggestions", scene.getAiSuggestions());
        data.put("submittedAt", scene.getSubmittedAt());

        try {
            String signed = sceneSubmissionDao.getSignedUrl(scene.getVideoUrl());
            data.put("videoSignedUrl", signed);
        } catch (Exception ignored) {}

        return ResponseEntity.ok(ApiResponse.ok("Scene retrieved", data));
    }

    @SuppressWarnings("unchecked")
    private void updateSubmittedVideoWithScene(String compositeVideoId, String assignmentId, String userId, SceneSubmission sceneSubmission) throws Exception {
        DocumentReference videoDocRef = db.collection("submittedVideos").document(compositeVideoId);
        DocumentSnapshot videoDoc = videoDocRef.get().get();
        
        Map<String, Object> sceneData = new HashMap<>();
        sceneData.put("sceneId", sceneSubmission.getId());
        sceneData.put("status", sceneSubmission.getStatus());
        
        if (videoDoc.exists()) {
            Map<String, Object> currentScenes = (Map<String, Object>) videoDoc.get("scenes");
            if (currentScenes == null) currentScenes = new HashMap<>();
            
            currentScenes.put(String.valueOf(sceneSubmission.getSceneNumber()), sceneData);
            
            int templateTotalScenes = getTemplateTotalScenes(assignmentId);
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
            
            // Auto-update publishStatus if all scenes approved
            if (approvedScenes == templateTotalScenes && templateTotalScenes > 0) {
                updates.put("publishStatus", "approved");
                updates.put("approvedAt", FieldValue.serverTimestamp());
            }
            
            videoDocRef.update(updates);
        } else {
            Map<String, Object> videoData = new HashMap<>();
            videoData.put("videoId", compositeVideoId);
            videoData.put("assignmentId", assignmentId);
            videoData.put("uploadedBy", userId);
            videoData.put("publishStatus", "pending");
            videoData.put("createdAt", FieldValue.serverTimestamp());
            videoData.put("lastUpdated", FieldValue.serverTimestamp());
            
            Map<String, Object> scenes = new HashMap<>();
            scenes.put(String.valueOf(sceneSubmission.getSceneNumber()), sceneData);
            videoData.put("scenes", scenes);
            
            int templateTotalScenes = getTemplateTotalScenes(assignmentId);
            videoData.put("progress", Map.of(
                "totalScenes", templateTotalScenes,
                "approved", 0,
                "pending", 1,
                "completionPercentage", 0.0
            ));
            
            videoDocRef.set(videoData);
            // Note: We don't update the original template's submittedVideos since we're using assignments now
        }
    }
    
    private int getTemplateTotalScenes(String assignmentId) throws Exception {
        ManualTemplate template = getTemplateByAssignmentId(assignmentId);
        return (template != null && template.getScenes() != null) ? template.getScenes().size() : 0;
    }
    
    /**
     * Get template by assignment ID only
     */
    private ManualTemplate getTemplateByAssignmentId(String assignmentId) throws Exception {
        com.example.demo.model.TemplateAssignment assignment = templateAssignmentDao.getAssignment(assignmentId);
        if (assignment == null || assignment.getTemplateSnapshot() == null) {
            throw new NoSuchElementException("Template assignment not found with ID: " + assignmentId);
        }
        return assignment.getTemplateSnapshot();
    }
    
    @SuppressWarnings("unchecked")
    private boolean checkGroupAIThreshold(String userId, double similarityScore) {
        try {
            // Get user's group
            DocumentSnapshot userDoc = db.collection("users").document(userId).get().get();
            if (!userDoc.exists()) return false;
            
            String groupId = userDoc.getString("groupId");
            if (groupId == null) return false;
            
            // Get group's AI threshold
            DocumentSnapshot groupDoc = db.collection("groups").document(groupId).get().get();
            if (!groupDoc.exists()) return false;
            
            Double aiThreshold = groupDoc.getDouble("aiApprovalThreshold");
            Boolean aiAutoApprovalEnabled = groupDoc.getBoolean("aiAutoApprovalEnabled");
            
            // Convert similarity score to percentage (0-100) for comparison
            double similarityPercentage = similarityScore * 100;
            // Normalize threshold: accept both 0-1 and 0-100 inputs
            double thresholdPercent = (aiThreshold != null && aiThreshold <= 1.0) ? aiThreshold * 100.0 : (aiThreshold != null ? aiThreshold : 0.0);
            
            // Auto-approve if enabled and score meets threshold
            return aiAutoApprovalEnabled != null && aiAutoApprovalEnabled && 
                   aiThreshold != null && similarityPercentage >= thresholdPercent;
        } catch (Exception e) {
            return false; // Default to manual approval if error
        }
    }

    /**
     * Determine automatic status based on group AI settings.
     * Returns "approved", "rejected", or null (no auto decision).
     */
    private String determineAutoStatus(String userId, double similarityScore) {
        try {
            DocumentSnapshot userDoc = db.collection("users").document(userId).get().get();
            if (!userDoc.exists()) return null;
            String groupId = userDoc.getString("groupId");
            if (groupId == null) return null;

            DocumentSnapshot groupDoc = db.collection("groups").document(groupId).get().get();
            if (!groupDoc.exists()) return null;

            Double aiThreshold = groupDoc.getDouble("aiApprovalThreshold");
            Boolean aiAutoApprovalEnabled = groupDoc.getBoolean("aiAutoApprovalEnabled");
            if (aiAutoApprovalEnabled == null || !aiAutoApprovalEnabled || aiThreshold == null) return null;

            double similarityPercentage = similarityScore * 100.0;
            double thresholdPercent = aiThreshold <= 1.0 ? aiThreshold * 100.0 : aiThreshold;

            return similarityPercentage >= thresholdPercent ? SceneSubmission.STATUS_APPROVED : SceneSubmission.STATUS_REJECTED;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get the original template video URL from template videoId
     * Uses VideoDao to fetch from exampleVideos collection
     */
    private String getTemplateVideoUrl(String videoId) {
        try {
            // Use VideoDao to get the video from exampleVideos collection
            Video video = videoDao.getVideoById(videoId);
            
            if (video != null && video.getUrl() != null) {
                log.info("Template video URL retrieved: {} for videoId: {}", video.getUrl(), videoId);
                return video.getUrl();
            }
            
            log.warn("Video not found in exampleVideos collection for videoId: {}", videoId);
            
        } catch (Exception e) {
            log.error("Error retrieving template video URL for videoId {}: {}", videoId, e.getMessage(), e);
        }
        
        // Fallback: construct URL with default pattern if VideoDao lookup fails
        String fallbackUrl = String.format("https://storage.googleapis.com/matrix-ads-bucket/videos/template/%s/template.mp4", videoId);
        log.warn("Using fallback URL for videoId {}: {}", videoId, fallbackUrl);
        return fallbackUrl;
    }
}
