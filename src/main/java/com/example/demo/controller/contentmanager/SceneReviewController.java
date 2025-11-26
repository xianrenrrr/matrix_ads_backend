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
            // Use assignmentId (stored in templateId field) for composite video ID
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
    private void updateSceneStatusInSubmittedVideos(String assignmentId, String userId, int sceneNumber, String newStatus) throws Exception {
            // Create composite video ID using assignmentId (not templateId!)
            String compositeVideoId = userId + "_" + assignmentId;
            
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
                        
                        // Get actual template scene count from assignment
                        int templateTotalScenes = getTemplateTotalScenes(assignmentId);
                        
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
                                System.out.println("✅ All scenes approved! Updated publishStatus to 'approved' for video: " + compositeVideoId);
                                
                                // Sync status to managerSubmissions
                                syncStatusToManagerSubmissions(assignmentId, compositeVideoId, "approved");
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
    
    /**
     * Sync submission status to managerSubmissions collection
     */
    private void syncStatusToManagerSubmissions(String assignmentId, String submissionId, String status) {
        try {
            com.example.demo.model.TemplateAssignment assignment = templateAssignmentDao.getAssignment(assignmentId);
            if (assignment != null && assignment.getPushedBy() != null) {
                managerSubmissionDao.updateSubmissionStatus(assignment.getPushedBy(), submissionId, status);
                System.out.println("✅ Synced status '" + status + "' to managerSubmissions for: " + submissionId);
            }
        } catch (Exception e) {
            System.err.println("Failed to sync status to managerSubmissions: " + e.getMessage());
        }
    }
    
    @Autowired
    private com.example.demo.dao.TemplateAssignmentDao templateAssignmentDao;
    
    @Autowired
    private com.example.demo.dao.ManagerSubmissionDao managerSubmissionDao;
    
    private int getTemplateTotalScenes(String assignmentId) throws Exception {
        // Get template from assignment snapshot
        com.example.demo.model.TemplateAssignment assignment = templateAssignmentDao.getAssignment(assignmentId);
        if (assignment != null && assignment.getTemplateSnapshot() != null) {
            ManualTemplate template = assignment.getTemplateSnapshot();
            return (template.getScenes() != null) ? template.getScenes().size() : 0;
        }
        return 0;
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

        // DAO handles signed URL generation
        String url = sceneSubmissionDao.getSignedUrl(submission.getVideoUrl());

        return ResponseEntity.ok(com.example.demo.api.ApiResponse.ok(i18nService.getMessage("operation.success", language), url));
    }
}
