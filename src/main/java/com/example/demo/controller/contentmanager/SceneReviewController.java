package com.example.demo.controller.contentmanager;

import com.example.demo.dao.SceneSubmissionDao;
import com.example.demo.dao.TemplateDao;
import com.example.demo.model.SceneSubmission;
import com.example.demo.model.ManualTemplate;
import com.example.demo.api.ApiResponse;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Content Manager Scene Review Controller
 * Handles scene approval/rejection and triggers video compilation
 */
@RestController
@RequestMapping("/content-manager/scenes")
@CrossOrigin(origins = {"http://localhost:4040", "https://matrix-ads-frontend.onrender.com"})
public class SceneReviewController {
    
    @Autowired
    private SceneSubmissionDao sceneSubmissionDao;
    
    @Autowired
    private TemplateDao templateDao;

    @Autowired
    private Firestore db;

    @Autowired(required = false)
    private com.example.demo.service.FirebaseStorageService firebaseStorageService;

    @Autowired
    private com.example.demo.service.I18nService i18nService;
    
    // REMOVED getSubmittedVideo() - DUPLICATE of ContentManager.java endpoint
    // Frontend uses: /content-manager/templates/submitted-videos/{compositeVideoId}
    
    // REMOVED unused endpoints - frontend doesn't use /pending or /template/{templateId}
    
    
    /**
     * Manual override for scene approval or rejection
     * POST /content-manager/scenes/{sceneId}/manual-override
     */
    @PostMapping("/{sceneId}/manual-override")
    public ResponseEntity<ApiResponse<Map<String, Object>>> manualOverrideScene(
            @PathVariable String sceneId,
            @RequestParam String reviewerId,
            @RequestBody Map<String, Object> requestBody) throws Exception {
        
        SceneSubmission submission = sceneSubmissionDao.findById(sceneId);
        if (submission == null) {
            throw new NoSuchElementException("Scene not found: " + sceneId);
        }
        
        String overrideReason = (String) requestBody.get("overrideReason");
        List<String> feedback = (List<String>) requestBody.get("feedback");
        boolean isApproval = feedback == null || feedback.isEmpty();
        
        // Simple approve or reject
        if (isApproval) {
            submission.approve(reviewerId);
            updateSceneStatusInSubmittedVideos(submission.getTemplateId(), submission.getUserId(), 
                submission.getSceneNumber(), "approved");
        } else {
            submission.reject(reviewerId, feedback);
            // Keep status as "pending" so creator can resubmit
        }
        
        sceneSubmissionDao.update(submission);
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("action", isApproval ? "approved" : "rejected");
        responseData.put("sceneSubmission", submission);
        
        String message = isApproval ? "Scene approved successfully" : "Scene rejected with feedback";
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }
    
    // Helper Methods
    
    /**
     * Update scene status in submittedVideos collection
     */
    @SuppressWarnings("unchecked")
    private void updateSceneStatusInSubmittedVideos(String templateId, String userId, int sceneNumber, String newStatus) throws Exception {
            // Create composite video ID
            String compositeVideoId = userId + "_" + templateId;
            
            // Get the submitted video document
            var videoDocRef = db.collection("submittedVideos").document(compositeVideoId);
            var videoDoc = videoDocRef.get().get();
            
            if (videoDoc.exists()) {
                Map<String, Object> videoData = videoDoc.getData();
                Map<String, Object> scenes = (Map<String, Object>) videoData.get("scenes");
                
                if (scenes != null) {
                    // Update the specific scene status
                    Map<String, Object> sceneData = (Map<String, Object>) scenes.get(String.valueOf(sceneNumber));
                    if (sceneData != null) {
                        sceneData.put("status", newStatus);
                        
                        // Update the document
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("scenes." + sceneNumber + ".status", newStatus);
                        updates.put("lastUpdated", com.google.cloud.firestore.FieldValue.serverTimestamp());
                        
                        // Recalculate progress (only pending and approved now)
                        int approvedCount = 0;
                        int pendingCount = 0;
                        
                        for (Object sceneObj : scenes.values()) {
                            if (sceneObj instanceof Map) {
                                Map<String, Object> scene = (Map<String, Object>) sceneObj;
                                String status = (String) scene.get("status");
                                if ("approved".equals(status)) approvedCount++;
                                else if ("pending".equals(status)) pendingCount++;
                            }
                        }
                        
                        // Get actual template scene count
                        int templateTotalScenes = getTemplateTotalScenes(templateId);
                        
                        updates.put("progress", Map.of(
                            "totalScenes", templateTotalScenes,
                            "approved", approvedCount,
                            "pending", pendingCount,
                            "completionPercentage", templateTotalScenes > 0 ? (double) approvedCount / templateTotalScenes * 100 : 0
                        ));
                        
                        // Update publishStatus if all template scenes are approved
                        if (approvedCount == templateTotalScenes && templateTotalScenes > 0) {
                            String currentPublishStatus = (String) videoData.get("publishStatus");
                            if (!"approved".equals(currentPublishStatus) && !"published".equals(currentPublishStatus)) {
                                updates.put("publishStatus", "approved");
                                updates.put("approvedAt", com.google.cloud.firestore.FieldValue.serverTimestamp());
                                System.out.println("Automatically updated publishStatus to 'approved' for video: " + compositeVideoId);
                            }
                        }
                        
                        videoDocRef.update(updates);
                        System.out.println("Updated scene " + sceneNumber + " status to '" + newStatus + "' in submittedVideos: " + compositeVideoId);
                    }
                }
        } else {
            System.err.println("SubmittedVideo not found for update: " + compositeVideoId);
        }
    }
    
    private int getTemplateTotalScenes(String templateId) throws Exception {
        ManualTemplate template = templateDao.getTemplate(templateId);
        return (template != null && template.getScenes() != null) ? template.getScenes().size() : 0;
    }

    /**
     * Return a playable (optionally signed) URL for a scene submission video
     * GET /content-manager/scenes/{sceneId}/stream
     */
    @GetMapping("/{sceneId}/stream")
    public ResponseEntity<com.example.demo.api.ApiResponse<String>> streamScene(@PathVariable String sceneId,
                                                                                @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);

        SceneSubmission submission = sceneSubmissionDao.findById(sceneId);
        if (submission == null || submission.getVideoUrl() == null || submission.getVideoUrl().isEmpty()) {
            throw new NoSuchElementException("Scene not found or missing videoUrl: " + sceneId);
        }

        String url = submission.getVideoUrl();
        // If Firebase signing available, generate a fresh signed URL for reliable playback in browser
        if (firebaseStorageService != null) {
            try {
                url = firebaseStorageService.generateSignedUrl(url);
            } catch (Exception e) {
                // fallback to original URL
            }
        }

        return ResponseEntity.ok(com.example.demo.api.ApiResponse.ok(i18nService.getMessage("operation.success", language), url));
    }
}
