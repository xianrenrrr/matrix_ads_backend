package com.example.demo.controller.contentmanager;

import com.example.demo.dao.SceneSubmissionDao;
import com.example.demo.model.SceneSubmission;
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
public class SceneReviewController {
    
    @Autowired
    private SceneSubmissionDao sceneSubmissionDao;
    
    @Autowired
    private Firestore db;
    
    // REMOVED getSubmittedVideo() - DUPLICATE of ContentManager.java endpoint
    // Frontend uses: /content-manager/templates/submitted-videos/{compositeVideoId}
    
    // REMOVED unused endpoints - frontend doesn't use /pending or /template/{templateId}
    
    
    /**
     * Manual override for scene approval or rejection
     * POST /content-manager/scenes/{sceneId}/manual-override
     */
    @PostMapping("/{sceneId}/manual-override")
    public ResponseEntity<Map<String, Object>> manualOverrideScene(
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
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("action", isApproval ? "approved" : "rejected");
        response.put("sceneSubmission", submission);
        
        return ResponseEntity.ok(response);
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
                        
                        updates.put("progress", Map.of(
                            "totalScenes", scenes.size(),
                            "approved", approvedCount,
                            "pending", pendingCount,
                            "completionPercentage", scenes.size() > 0 ? (double) approvedCount / scenes.size() * 100 : 0
                        ));
                        
                        // Update publishStatus if all scenes are approved
                        if (approvedCount == scenes.size() && scenes.size() > 0) {
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
}