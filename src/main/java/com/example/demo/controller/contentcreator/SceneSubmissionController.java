package com.example.demo.controller.contentcreator;

import com.example.demo.dao.SceneSubmissionDao;
import com.example.demo.dao.TemplateDao;
import com.example.demo.dao.VideoDao;
import com.example.demo.model.SceneSubmission;
import com.example.demo.api.ApiResponse;
import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Video;
// ComparisonAIService removed - using QwenSceneComparisonService
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
    private com.example.demo.dao.UserDao userDao;
    
    @Autowired
    private com.example.demo.dao.GroupDao groupDao;
    
    @Autowired
    private com.example.demo.dao.SubmittedVideoDao submittedVideoDao;
    
    // ComparisonAIService removed - using QwenSceneComparisonService
    
    @Autowired
    private com.example.demo.ai.services.QwenSceneComparisonService qwenComparisonService;  // New Qwen-based comparison
    
    @Autowired
    private VideoDao videoDao;
    
    @Autowired
    private com.example.demo.dao.TemplateAssignmentDao templateAssignmentDao;
    
    @Autowired
    private com.example.demo.ai.services.KeyframeExtractionService keyframeExtractionService;

    
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
        
        // For manual templates, each scene has its own videoId
        // For AI templates, use the template's videoId
        String videoIdToUse = templateScene.getVideoId() != null ? templateScene.getVideoId() : template.getVideoId();
        final String templateVideoUrl = getTemplateVideoUrl(videoIdToUse);  // May be null for manual templates without video
        final String userVideoUrl = sceneSubmission.getVideoUrl();
        
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                // Ensure template scene has keyframe URL (required for comparison)
                String templateKeyframeUrl = templateScene.getKeyframeUrl();
                
                // If no keyframe URL, try to extract from scene's video (for manual templates)
                if ((templateKeyframeUrl == null || templateKeyframeUrl.isEmpty()) && templateVideoUrl != null) {
                    log.info("Template scene {} has no keyframe URL, extracting from scene video", sceneNumber);
                    try {
                        // Extract keyframe from middle of the scene video
                        java.time.Duration keyframeTime = java.time.Duration.ofSeconds(
                            templateScene.getSceneDurationInSeconds() / 2
                        );
                        templateKeyframeUrl = keyframeExtractionService.extractKeyframe(
                            templateVideoUrl, keyframeTime, null
                        );
                        // Update the scene with the extracted keyframe URL for future use
                        templateScene.setKeyframeUrl(templateKeyframeUrl);
                        log.info("Extracted keyframe URL for template scene {}: {}", sceneNumber, templateKeyframeUrl);
                    } catch (Exception e) {
                        log.error("Failed to extract keyframe from template video: {}", e.getMessage());
                    }
                }
                
                // Final check - if still no keyframe URL, cannot compare
                if (templateKeyframeUrl == null || templateKeyframeUrl.isEmpty()) {
                    log.error("Template scene {} has no keyframe URL and extraction failed, cannot perform AI comparison", sceneNumber);
                    // Update submission with error message
                    SceneSubmission errorSubmission = sceneSubmissionDao.findById(finalSceneId);
                    if (errorSubmission != null) {
                        errorSubmission.setSimilarityScore(0.5);  // Default middle score
                        List<String> errorSuggestions = new ArrayList<>();
                        errorSuggestions.add("模板场景缺少关键帧图片，无法进行AI对比。请联系管理员。");
                        errorSubmission.setAiSuggestions(errorSuggestions);
                        sceneSubmissionDao.update(errorSubmission);
                    }
                    return;
                }
                
                log.info("Starting async AI comparison for scene {} using NEW direct 2-image method", sceneNumber);
                log.info("Template keyframe: {}", templateKeyframeUrl);
                log.info("User video: {}", userVideoUrl);
                
                // Get the scene submission to access the thumbnail URL (already extracted during upload)
                SceneSubmission currentSubmission = sceneSubmissionDao.findById(finalSceneId);
                String userThumbnailUrl = currentSubmission != null ? currentSubmission.getThumbnailUrl() : null;
                
                // NEW: Use direct 2-image comparison with purpose-driven evaluation
                // Pass user thumbnail URL to avoid re-extracting keyframe
                com.example.demo.ai.services.ComparisonResult comparisonResult = qwenComparisonService.compareWithDirectVL(
                    templateScene, userVideoUrl, userThumbnailUrl, "zh");
                
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
                    
                    // Update parent submittedVideos document with new scene status
                    try {
                        updateSubmittedVideoWithScene(compositeVideoId, assignmentId, userId, updatedSubmission);
                        log.info("✅ Updated parent submittedVideos document after AI comparison");
                    } catch (Exception e) {
                        log.error("❌ Failed to update parent submittedVideos document: {}", e.getMessage());
                    }
                    
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
        com.example.demo.model.SubmittedVideo video = submittedVideoDao.findById(compositeVideoId);
        
        if (video == null) {
            throw new NoSuchElementException("No submission found");
        }
        
        // Convert to Map for response
        Map<String, Object> videoData = new HashMap<>();
        videoData.put("id", video.getId());
        videoData.put("uploadedBy", video.getUploadedBy());
        videoData.put("assignmentId", video.getAssignmentId());
        videoData.put("publishStatus", video.getPublishStatus());
        videoData.put("progress", video.getProgress());
        videoData.put("createdAt", video.getCreatedAt());
        videoData.put("lastUpdated", video.getLastUpdated());
        
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
        com.example.demo.model.SubmittedVideo video = submittedVideoDao.findById(compositeVideoId);
        
        Map<String, Object> sceneData = new HashMap<>();
        sceneData.put("sceneId", sceneSubmission.getId());
        sceneData.put("status", sceneSubmission.getStatus());
        
        if (video != null) {
            Map<String, Object> currentScenes = video.getScenes();
            if (currentScenes == null) currentScenes = new HashMap<>();
            
            currentScenes.put(String.valueOf(sceneSubmission.getSceneNumber()), sceneData);
            video.setScenes(currentScenes);
            
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
            
            Map<String, Object> progress = new HashMap<>();
            progress.put("totalScenes", templateTotalScenes);
            progress.put("approved", approvedScenes);
            progress.put("pending", pendingScenes);
            progress.put("completionPercentage", templateTotalScenes > 0 ? (double) approvedScenes / templateTotalScenes * 100 : 0);
            video.setProgress(progress);
            video.setLastUpdated(new Date());
            
            // Auto-update publishStatus if all scenes approved
            if (approvedScenes == templateTotalScenes && templateTotalScenes > 0) {
                video.setPublishStatus("approved");
                video.setApprovedAt(new Date());
            }
            
            submittedVideoDao.update(video);
        } else {
            // Create new submitted video using DAO
            com.example.demo.model.SubmittedVideo newVideo = new com.example.demo.model.SubmittedVideo();
            newVideo.setId(compositeVideoId);
            newVideo.setAssignmentId(assignmentId);
            newVideo.setUploadedBy(userId);
            newVideo.setPublishStatus("pending");
            newVideo.setCreatedAt(new java.util.Date());
            newVideo.setLastUpdated(new java.util.Date());
            
            Map<String, Object> scenes = new HashMap<>();
            scenes.put(String.valueOf(sceneSubmission.getSceneNumber()), sceneData);
            newVideo.setScenes(scenes);
            
            int templateTotalScenes = getTemplateTotalScenes(assignmentId);
            Map<String, Object> progress = new HashMap<>();
            progress.put("totalScenes", templateTotalScenes);
            progress.put("approved", 0);
            progress.put("pending", 1);
            progress.put("completionPercentage", 0.0);
            newVideo.setProgress(progress);
            
            submittedVideoDao.save(newVideo);
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
            // Get user's group using DAO
            com.example.demo.model.User user = userDao.findById(userId);
            if (user == null || user.getGroupId() == null) return false;
            
            // Get group's AI threshold using DAO
            com.example.demo.model.Group group = groupDao.findById(user.getGroupId());
            if (group == null) return false;
            
            Double aiThreshold = group.getAiApprovalThreshold();
            Boolean aiAutoApprovalEnabled = group.isAiAutoApprovalEnabled();
            
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
            com.example.demo.model.User user = userDao.findById(userId);
            if (user == null || user.getGroupId() == null) return null;
            
            com.example.demo.model.Group group = groupDao.findById(user.getGroupId());
            if (group == null) return null;

            Double aiThreshold = group.getAiApprovalThreshold();
            Boolean aiAutoApprovalEnabled = group.isAiAutoApprovalEnabled();
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
        // Handle null videoId (manual templates without video)
        if (videoId == null || videoId.isEmpty()) {
            log.warn("Template has no videoId (manual template), cannot retrieve template video URL");
            return null;  // Return null for manual templates without video
        }
        
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
