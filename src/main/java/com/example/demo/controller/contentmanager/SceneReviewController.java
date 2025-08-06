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
            List<SceneSubmission> submissions = sceneSubmissionDao.findByTemplateId(templateId);
            
            // Group by user and status
            Map<String, List<SceneSubmission>> submissionsByUser = new HashMap<>();
            Map<String, Integer> statusCounts = new HashMap<>();
            
            for (SceneSubmission submission : submissions) {
                submissionsByUser.computeIfAbsent(submission.getUserId(), k -> new ArrayList<>()).add(submission);
                statusCounts.merge(submission.getStatus(), 1, Integer::sum);
            }
            
            // Also get compiled scene data from submittedVideos collection
            Map<String, Object> submittedVideosData = getSubmittedVideosForTemplate(templateId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("templateId", templateId);
            response.put("totalSubmissions", submissions.size());
            response.put("submissions", submissions);
            response.put("submissionsByUser", submissionsByUser);
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
            
            // Approve the scene
            submission.approve(reviewerId);
            if (feedback != null) {
                submission.setFeedback(feedback);
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