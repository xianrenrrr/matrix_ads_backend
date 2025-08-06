package com.example.demo.controller.contentmanager;

import com.example.demo.dao.SceneSubmissionDao;
import com.example.demo.dao.CompiledVideoDao;
import com.example.demo.dao.UserDao;
import com.example.demo.model.SceneSubmission;
import com.example.demo.model.CompiledVideo;
import com.example.demo.service.VideoCompilationService;
import com.example.demo.service.WorkflowAutomationService;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ExecutionException;

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
    private CompiledVideoDao compiledVideoDao;
    
    @Autowired
    private UserDao userDao;
    
    @Autowired(required = false)
    private VideoCompilationService videoCompilationService;
    
    @Autowired
    private WorkflowAutomationService workflowAutomationService;
    
    @Autowired
    private Firestore db;
    
    /**
     * Get all pending scene submissions for review
     * GET /content-manager/scenes/pending
     */
    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> getPendingSubmissions() {
        try {
            List<SceneSubmission> pendingSubmissions = sceneSubmissionDao.findPendingSubmissionsForReview();
            
            // Group by template and user for better organization
            Map<String, Map<String, List<SceneSubmission>>> groupedSubmissions = new HashMap<>();
            
            for (SceneSubmission submission : pendingSubmissions) {
                groupedSubmissions
                    .computeIfAbsent(submission.getTemplateId(), k -> new HashMap<>())
                    .computeIfAbsent(submission.getUserId(), k -> new ArrayList<>())
                    .add(submission);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalPending", pendingSubmissions.size());
            response.put("pendingSubmissions", pendingSubmissions);
            response.put("groupedSubmissions", groupedSubmissions);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("Failed to get pending submissions: " + e.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Get scene submissions for a specific template
     * GET /content-manager/scenes/template/{templateId}
     */
    @GetMapping("/template/{templateId}")
    public ResponseEntity<Map<String, Object>> getTemplateSubmissions(@PathVariable String templateId) {
        try {
            // Get compiled scene data from submittedVideos collection (the primary source)
            Map<String, Object> submittedVideosData = getSubmittedVideosForTemplate(templateId);
            
            // Calculate status counts from submittedVideos data
            Map<String, Integer> statusCounts = new HashMap<>();
            statusCounts.put("approved", 0);
            statusCounts.put("pending", 0);
            statusCounts.put("rejected", 0);
            
            int totalScenes = 0;
            
            for (Object videoDataObj : submittedVideosData.values()) {
                if (videoDataObj instanceof Map) {
                    Map<String, Object> videoData = (Map<String, Object>) videoDataObj;
                    Map<String, Object> stats = (Map<String, Object>) videoData.get("sceneStats");
                    if (stats != null) {
                        statusCounts.merge("approved", (Integer) stats.get("approved"), Integer::sum);
                        statusCounts.merge("pending", (Integer) stats.get("pending"), Integer::sum);
                        statusCounts.merge("rejected", (Integer) stats.get("rejected"), Integer::sum);
                        totalScenes += (Integer) stats.get("total");
                    }
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("templateId", templateId);
            response.put("totalScenes", totalScenes);
            response.put("statusCounts", statusCounts);
            response.put("submittedVideos", submittedVideosData);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("Failed to get template submissions: " + e.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Approve a scene submission
     * POST /content-manager/scenes/{sceneId}/approve
     */
    @PostMapping("/{sceneId}/approve")
    public ResponseEntity<Map<String, Object>> approveScene(
            @PathVariable String sceneId,
            @RequestParam String reviewerId,
            @RequestBody(required = false) Map<String, Object> requestBody) {
        
        try {
            SceneSubmission submission = sceneSubmissionDao.findById(sceneId);
            if (submission == null) {
                return createErrorResponse("Scene submission not found", HttpStatus.NOT_FOUND);
            }
            
            if (!"pending".equals(submission.getStatus())) {
                return createErrorResponse("Can only approve pending submissions", HttpStatus.BAD_REQUEST);
            }
            
            // Get feedback if provided
            List<String> feedback = null;
            if (requestBody != null && requestBody.containsKey("feedback")) {
                feedback = (List<String>) requestBody.get("feedback");
            }
            
            // Check if this is a manual override (below AI threshold)
            boolean isManualOverride = false;
            if (requestBody != null && requestBody.containsKey("manualOverride")) {
                isManualOverride = (Boolean) requestBody.get("manualOverride");
            }
            
            // Approve the scene
            submission.approve(reviewerId);
            if (feedback != null) {
                submission.setFeedback(feedback);
            }
            
            // Add manual override tracking if needed
            if (isManualOverride) {
                String overrideReason = requestBody != null ? (String) requestBody.get("overrideReason") : null;
                if (overrideReason == null || overrideReason.trim().isEmpty()) {
                    return createErrorResponse("Override reason is required for manual approvals", HttpStatus.BAD_REQUEST);
                }
                
                // Add override metadata to the submission
                Map<String, Object> overrideMetadata = new HashMap<>();
                overrideMetadata.put("isManualOverride", true);
                overrideMetadata.put("overrideReason", overrideReason);
                overrideMetadata.put("overrideBy", reviewerId);
                overrideMetadata.put("overrideAt", new Date());
                overrideMetadata.put("originalSimilarityScore", submission.getSimilarityScore());
                
                submission.getQualityMetrics().putAll(overrideMetadata);
                System.out.println("Manual override applied for scene " + sceneId + " by " + reviewerId + ": " + overrideReason);
            }
            
            sceneSubmissionDao.update(submission);
            
            // Process approval through workflow automation
            Map<String, Object> workflowResult = workflowAutomationService.processSceneApproval(sceneId, reviewerId);
            boolean compilationTriggered = (Boolean) workflowResult.getOrDefault("compilationTriggered", false);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Scene approved successfully");
            response.put("sceneSubmission", submission);
            response.put("compilationTriggered", compilationTriggered);
            response.put("workflowActions", workflowResult.getOrDefault("actionsPerformed", new ArrayList<>()));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("Failed to approve scene: " + e.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Reject a scene submission
     * POST /content-manager/scenes/{sceneId}/reject
     */
    @PostMapping("/{sceneId}/reject")
    public ResponseEntity<Map<String, Object>> rejectScene(
            @PathVariable String sceneId,
            @RequestParam String reviewerId,
            @RequestBody Map<String, Object> requestBody) {
        
        try {
            SceneSubmission submission = sceneSubmissionDao.findById(sceneId);
            if (submission == null) {
                return createErrorResponse("Scene submission not found", HttpStatus.NOT_FOUND);
            }
            
            if (!"pending".equals(submission.getStatus())) {
                return createErrorResponse("Can only reject pending submissions", HttpStatus.BAD_REQUEST);
            }
            
            // Get feedback (required for rejection)
            List<String> feedback = (List<String>) requestBody.get("feedback");
            if (feedback == null || feedback.isEmpty()) {
                return createErrorResponse("Feedback is required when rejecting a scene", HttpStatus.BAD_REQUEST);
            }
            
            // Reject the scene
            submission.reject(reviewerId, feedback);
            sceneSubmissionDao.update(submission);
            
            // Process rejection through workflow automation
            Map<String, Object> workflowResult = workflowAutomationService.processSceneRejection(sceneId, reviewerId, feedback);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Scene rejected with feedback");
            response.put("sceneSubmission", submission);
            response.put("workflowActions", workflowResult.getOrDefault("actionsPerformed", new ArrayList<>()));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("Failed to reject scene: " + e.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Manual override to approve a scene that doesn't meet AI threshold
     * POST /content-manager/scenes/{sceneId}/manual-override
     */
    @PostMapping("/{sceneId}/manual-override")
    public ResponseEntity<Map<String, Object>> manualOverrideScene(
            @PathVariable String sceneId,
            @RequestParam String reviewerId,
            @RequestBody Map<String, Object> requestBody) {
        
        try {
            SceneSubmission submission = sceneSubmissionDao.findById(sceneId);
            if (submission == null) {
                return createErrorResponse("Scene submission not found", HttpStatus.NOT_FOUND);
            }
            
            if (!"pending".equals(submission.getStatus())) {
                return createErrorResponse("Can only override pending submissions", HttpStatus.BAD_REQUEST);
            }
            
            // Get required override reason
            String overrideReason = (String) requestBody.get("overrideReason");
            if (overrideReason == null || overrideReason.trim().isEmpty()) {
                return createErrorResponse("Override reason is required", HttpStatus.BAD_REQUEST);
            }
            
            // Optional feedback
            List<String> feedback = (List<String>) requestBody.get("feedback");
            
            // Approve the scene with override
            submission.approve(reviewerId);
            if (feedback != null && !feedback.isEmpty()) {
                submission.setFeedback(feedback);
            }
            
            // Add manual override metadata
            Map<String, Object> overrideMetadata = new HashMap<>();
            overrideMetadata.put("isManualOverride", true);
            overrideMetadata.put("overrideReason", overrideReason.trim());
            overrideMetadata.put("overrideBy", reviewerId);
            overrideMetadata.put("overrideAt", new Date());
            overrideMetadata.put("originalSimilarityScore", submission.getSimilarityScore());
            
            // Merge with existing quality metrics
            Map<String, Object> qualityMetrics = submission.getQualityMetrics();
            if (qualityMetrics == null) {
                qualityMetrics = new HashMap<>();
            }
            qualityMetrics.putAll(overrideMetadata);
            submission.setQualityMetrics(qualityMetrics);
            
            sceneSubmissionDao.update(submission);
            
            // Process approval through workflow automation
            Map<String, Object> workflowResult = workflowAutomationService.processSceneApproval(sceneId, reviewerId);
            boolean compilationTriggered = (Boolean) workflowResult.getOrDefault("compilationTriggered", false);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Scene manually overridden and approved");
            response.put("sceneSubmission", submission);
            response.put("isManualOverride", true);
            response.put("overrideReason", overrideReason);
            response.put("compilationTriggered", compilationTriggered);
            response.put("workflowActions", workflowResult.getOrDefault("actionsPerformed", new ArrayList<>()));
            
            System.out.println("Manual override applied for scene " + sceneId + " by " + reviewerId + ": " + overrideReason);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("Failed to manually override scene: " + e.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Bulk approve multiple scenes
     * POST /content-manager/scenes/bulk-approve
     */
    @PostMapping("/bulk-approve")
    public ResponseEntity<Map<String, Object>> bulkApproveScenes(
            @RequestBody Map<String, Object> requestBody) {
        
        try {
            List<String> sceneIds = (List<String>) requestBody.get("sceneIds");
            String reviewerId = (String) requestBody.get("reviewerId");
            
            if (sceneIds == null || sceneIds.isEmpty()) {
                return createErrorResponse("Scene IDs are required", HttpStatus.BAD_REQUEST);
            }
            
            if (reviewerId == null) {
                return createErrorResponse("Reviewer ID is required", HttpStatus.BAD_REQUEST);
            }
            
            // Update all scenes to approved status
            sceneSubmissionDao.updateMultipleStatuses(sceneIds, "approved", reviewerId);
            
            // Check for compilation triggers
            Set<String> compilationTriggered = new HashSet<>();
            for (String sceneId : sceneIds) {
                SceneSubmission submission = sceneSubmissionDao.findById(sceneId);
                if (submission != null) {
                    String key = submission.getTemplateId() + ":" + submission.getUserId();
                    if (!compilationTriggered.contains(key)) {
                        boolean triggered = checkAndTriggerCompilation(
                            submission.getTemplateId(), submission.getUserId(), reviewerId);
                        if (triggered) {
                            compilationTriggered.add(key);
                        }
                    }
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("Approved %d scenes", sceneIds.size()));
            response.put("approvedCount", sceneIds.size());
            response.put("compilationsTriggered", compilationTriggered.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("Failed to bulk approve scenes: " + e.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Get submissions ready for publishing (approved status)
     * GET /content-manager/scenes/approved
     */
    @GetMapping("/approved")
    public ResponseEntity<Map<String, Object>> getApprovedSubmissions() {
        try {
            // Get all submittedVideos with approved publishStatus
            List<Map<String, Object>> approvedSubmissions = new ArrayList<>();
            
            // Query Firestore for approved submissions
            var query = db.collection("submittedVideos")
                         .whereEqualTo("publishStatus", "approved")
                         .orderBy("approvedAt", com.google.cloud.firestore.Query.Direction.DESCENDING);
            
            var querySnapshot = query.get().get();
            
            for (var document : querySnapshot.getDocuments()) {
                Map<String, Object> submissionData = document.getData();
                submissionData.put("id", document.getId());
                
                // Add template information
                String templateId = (String) submissionData.get("templateId");
                if (templateId != null) {
                    var templateDoc = db.collection("templates").document(templateId).get().get();
                    if (templateDoc.exists()) {
                        submissionData.put("templateTitle", templateDoc.getString("title"));
                        submissionData.put("templateDescription", templateDoc.getString("description"));
                    }
                }
                
                // Add user information
                String userId = (String) submissionData.get("uploadedBy");
                if (userId != null) {
                    var userDoc = db.collection("users").document(userId).get().get();
                    if (userDoc.exists()) {
                        submissionData.put("userName", userDoc.getString("name"));
                        submissionData.put("userEmail", userDoc.getString("email"));
                    }
                }
                
                approvedSubmissions.add(submissionData);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalApproved", approvedSubmissions.size());
            response.put("approvedSubmissions", approvedSubmissions);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("Failed to get approved submissions: " + e.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Publish an approved submission
     * POST /content-manager/scenes/submission/{submissionId}/publish
     */
    @PostMapping("/submission/{submissionId}/publish")
    public ResponseEntity<Map<String, Object>> publishSubmission(
            @PathVariable String submissionId,
            @RequestParam String publisherId) {
        
        try {
            var submissionRef = db.collection("submittedVideos").document(submissionId);
            var submissionDoc = submissionRef.get().get();
            
            if (!submissionDoc.exists()) {
                return createErrorResponse("Submission not found", HttpStatus.NOT_FOUND);
            }
            
            String currentStatus = submissionDoc.getString("publishStatus");
            if (!"approved".equals(currentStatus)) {
                return createErrorResponse("Can only publish approved submissions", HttpStatus.BAD_REQUEST);
            }
            
            // Update publishStatus to published
            Map<String, Object> updates = new HashMap<>();
            updates.put("publishStatus", "published");
            updates.put("publishedAt", com.google.cloud.firestore.FieldValue.serverTimestamp());
            updates.put("publishedBy", publisherId);
            
            submissionRef.update(updates);
            
            // Notify the content creator
            String userId = submissionDoc.getString("uploadedBy");
            if (userId != null) {
                var userRef = db.collection("users").document(userId);
                String notifId = UUID.randomUUID().toString();
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "submission_published");
                notification.put("message", "Your video submission has been published!");
                notification.put("submissionId", submissionId);
                notification.put("templateId", submissionDoc.getString("templateId"));
                notification.put("timestamp", System.currentTimeMillis());
                notification.put("read", false);
                
                userRef.update("notifications." + notifId, notification);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Submission published successfully");
            response.put("submissionId", submissionId);
            response.put("publishedBy", publisherId);
            
            System.out.println("Submission " + submissionId + " published by " + publisherId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("Failed to publish submission: " + e.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Get published submissions history
     * GET /content-manager/scenes/published
     */
    @GetMapping("/published") 
    public ResponseEntity<Map<String, Object>> getPublishedSubmissions(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String province) {
        
        try {
            var query = db.collection("submittedVideos")
                         .whereEqualTo("publishStatus", "published")
                         .orderBy("publishedAt", com.google.cloud.firestore.Query.Direction.DESCENDING)
                         .limit(limit);
            
            var querySnapshot = query.get().get();
            List<Map<String, Object>> publishedSubmissions = new ArrayList<>();
            Map<String, List<Map<String, Object>>> groupedByRegion = new HashMap<>();
            
            for (var document : querySnapshot.getDocuments()) {
                Map<String, Object> submissionData = document.getData();
                submissionData.put("id", document.getId());
                
                // Add template information
                String templateId = (String) submissionData.get("templateId");
                if (templateId != null) {
                    var templateDoc = db.collection("templates").document(templateId).get().get();
                    if (templateDoc.exists()) {
                        submissionData.put("templateTitle", templateDoc.getString("title"));
                    }
                }
                
                // Add user information including region/province if available
                String userId = (String) submissionData.get("uploadedBy");
                if (userId != null) {
                    var userDoc = db.collection("users").document(userId).get().get();
                    if (userDoc.exists()) {
                        submissionData.put("userName", userDoc.getString("name"));
                        String userRegion = userDoc.getString("region");
                        String userProvince = userDoc.getString("province");
                        submissionData.put("userRegion", userRegion);
                        submissionData.put("userProvince", userProvince);
                        
                        // Group by region/province for filtering
                        if (region == null || region.equals(userRegion)) {
                            if (province == null || province.equals(userProvince)) {
                                publishedSubmissions.add(submissionData);
                                
                                // Group for statistics
                                String groupKey = userRegion != null ? userRegion : "Unknown Region";
                                groupedByRegion.computeIfAbsent(groupKey, k -> new ArrayList<>())
                                               .add(submissionData);
                            }
                        }
                    } else {
                        if (region == null && province == null) {
                            publishedSubmissions.add(submissionData);
                        }
                    }
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalPublished", publishedSubmissions.size());
            response.put("publishedSubmissions", publishedSubmissions);
            response.put("groupedByRegion", groupedByRegion);
            response.put("filterApplied", Map.of(
                "region", region != null ? region : "all",
                "province", province != null ? province : "all"
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("Failed to get published submissions: " + e.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Get review statistics for a manager
     * GET /content-manager/scenes/stats/{reviewerId}
     */
    @GetMapping("/stats/{reviewerId}")
    public ResponseEntity<Map<String, Object>> getReviewStats(@PathVariable String reviewerId) {
        try {
            List<SceneSubmission> reviewedSubmissions = sceneSubmissionDao.findSubmissionsByReviewer(reviewerId);
            
            int totalReviewed = reviewedSubmissions.size();
            int approved = (int) reviewedSubmissions.stream().filter(s -> "approved".equals(s.getStatus())).count();
            int rejected = (int) reviewedSubmissions.stream().filter(s -> "rejected".equals(s.getStatus())).count();
            
            // Calculate average similarity score
            double avgSimilarity = reviewedSubmissions.stream()
                .filter(s -> s.getSimilarityScore() != null)
                .mapToDouble(SceneSubmission::getSimilarityScore)
                .average()
                .orElse(0.0);
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("reviewerId", reviewerId);
            stats.put("totalReviewed", totalReviewed);
            stats.put("approved", approved);
            stats.put("rejected", rejected);
            stats.put("approvalRate", totalReviewed > 0 ? (double) approved / totalReviewed * 100 : 0);
            stats.put("averageSimilarityScore", avgSimilarity);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("stats", stats);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("Failed to get review stats: " + e.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    // Helper Methods
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getSubmittedVideosForTemplate(String templateId) {
        try {
            Map<String, Object> result = new HashMap<>();
            
            // Query submittedVideos collection for documents where templateId matches
            // Since we're using composite IDs (userId_templateId), we need to query by templateId field
            var query = db.collection("submittedVideos")
                .whereEqualTo("templateId", templateId);
            
            var querySnapshot = query.get().get();
            
            for (var document : querySnapshot.getDocuments()) {
                String videoId = document.getId();
                Map<String, Object> videoData = document.getData();
                
                if (videoData != null) {
                    // Extract scenes data
                    Object scenesObj = videoData.get("scenes");
                    if (scenesObj instanceof Map) {
                        Map<String, Object> scenes = (Map<String, Object>) scenesObj;
                        videoData.put("scenes", scenes);
                        videoData.put("sceneCount", scenes.size());
                        
                        // Calculate summary stats for this video
                        int approved = 0, pending = 0, rejected = 0;
                        for (Object sceneObj : scenes.values()) {
                            if (sceneObj instanceof Map) {
                                Map<String, Object> scene = (Map<String, Object>) sceneObj;
                                String status = (String) scene.get("status");
                                if ("approved".equals(status)) approved++;
                                else if ("pending".equals(status)) pending++;
                                else if ("rejected".equals(status)) rejected++;
                            }
                        }
                        
                        videoData.put("sceneStats", Map.of(
                            "approved", approved,
                            "pending", pending,
                            "rejected", rejected,
                            "total", scenes.size()
                        ));
                    }
                    
                    result.put(videoId, videoData);
                }
            }
            
            return result;
            
        } catch (Exception e) {
            System.err.println("Error retrieving submitted videos for template " + templateId + ": " + e.getMessage());
            return new HashMap<>();
        }
    }
    
    private boolean checkAndTriggerCompilation(String templateId, String userId, String reviewerId) {
        try {
            // Get all approved scenes for this user/template
            List<SceneSubmission> approvedScenes = sceneSubmissionDao.getApprovedScenesInOrder(templateId, userId);
            
            // Check if we have a template to know the expected scene count
            // For now, assume completion if we have approved scenes
            if (!approvedScenes.isEmpty()) {
                // Check if compilation already exists
                CompiledVideo existing = compiledVideoDao.findByTemplateIdAndUserId(templateId, userId);
                if (existing == null || "failed".equals(existing.getStatus())) {
                    // Trigger compilation
                    if (videoCompilationService != null) {
                        return videoCompilationService.triggerCompilation(templateId, userId, 
                            approvedScenes.stream().map(SceneSubmission::getId).toList(), reviewerId);
                    } else {
                        // Create compilation record without actual compilation
                        CompiledVideo compiledVideo = new CompiledVideo(templateId, userId, 
                            approvedScenes.stream().map(SceneSubmission::getId).toList());
                        compiledVideo.setCompiledBy(reviewerId);
                        compiledVideo.setStatus("completed"); // Mock completion
                        compiledVideoDao.save(compiledVideo);
                        return true;
                    }
                }
            }
            
            return false;
            
        } catch (Exception e) {
            System.err.println("Error checking compilation trigger: " + e.getMessage());
            return false;
        }
    }
    
    private ResponseEntity<Map<String, Object>> createErrorResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }
}