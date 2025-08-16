package com.example.demo.service;

import com.example.demo.dao.SceneSubmissionDao;
import com.example.demo.dao.CompiledVideoDao;
import com.example.demo.dao.TemplateDao;
import com.example.demo.dao.UserDao;
import com.example.demo.model.SceneSubmission;
import com.example.demo.model.CompiledVideo;
import com.example.demo.model.ManualTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Workflow Automation Service Implementation
 * Provides intelligent automation for scene submission workflows
 */
@Service
public class WorkflowAutomationServiceImpl implements WorkflowAutomationService {
    
    @Autowired
    private SceneSubmissionDao sceneSubmissionDao;
    
    @Autowired
    private CompiledVideoDao compiledVideoDao;
    
    @Autowired
    private TemplateDao templateDao;
    
    @Autowired
    private UserDao userDao;
    
    @Autowired(required = false)
    private VideoCompilationService videoCompilationService;
    
    @Autowired(required = false)
    private NotificationService notificationService;
    
    @Override
    public Map<String, Object> processSceneSubmission(SceneSubmission sceneSubmission) throws ExecutionException, InterruptedException {
        Map<String, Object> result = new HashMap<>();
        List<String> actionsPerformed = new ArrayList<>();
        
        try {
            // Log submission
            System.out.println("Processing scene submission: " + sceneSubmission.getId() + 
                " for template " + sceneSubmission.getTemplateId());
            
            // Send notification to managers about new submission
            if (notificationService != null) {
                Map<String, Object> notificationData = new HashMap<>();
                notificationData.put("sceneSubmission", sceneSubmission);
                notificationData.put("action", "submitted");
                
                sendWorkflowNotifications("scene_submitted", notificationData);
                actionsPerformed.add("Notification sent to content managers");
            }
            
            // Check if this is a resubmission
            if (sceneSubmission.getResubmissionCount() > 0) {
                actionsPerformed.add("Processed as resubmission (attempt " + (sceneSubmission.getResubmissionCount() + 1) + ")");
            }
            
            // Auto-prioritize based on similarity score
            if (sceneSubmission.getSimilarityScore() != null) {
                if (sceneSubmission.getSimilarityScore() >= 0.9) {
                    actionsPerformed.add("High-quality submission flagged for priority review");
                } else if (sceneSubmission.getSimilarityScore() < 0.6) {
                    actionsPerformed.add("Low similarity score flagged for detailed review");
                }
            }
            
            result.put("success", true);
            result.put("sceneId", sceneSubmission.getId());
            result.put("actionsPerformed", actionsPerformed);
            
        } catch (Exception e) {
            System.err.println("Error processing scene submission: " + e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public boolean isReadyForCompilation(String templateId, String userId) throws ExecutionException, InterruptedException {
        try {
            // Get template to know expected scene count
            ManualTemplate template = templateDao.getTemplate(templateId);
            if (template == null) {
                return false;
            }
            
            int expectedScenes = template.getScenes().size();
            
            // Check if all scenes are approved
            List<SceneSubmission> approvedScenes = sceneSubmissionDao.getApprovedScenesInOrder(templateId, userId);
            
            // Ensure we have all scenes approved and in correct order
            if (approvedScenes.size() != expectedScenes) {
                return false;
            }
            
            // Verify scene numbers are sequential
            Set<Integer> sceneNumbers = approvedScenes.stream()
                .map(SceneSubmission::getSceneNumber)
                .collect(Collectors.toSet());
                
            for (int i = 1; i <= expectedScenes; i++) {
                if (!sceneNumbers.contains(i)) {
                    return false;
                }
            }
            
            // Check if compilation already exists
            CompiledVideo existing = compiledVideoDao.findByTemplateIdAndUserId(templateId, userId);
            return existing == null || "failed".equals(existing.getStatus());
            
        } catch (Exception e) {
            System.err.println("Error checking compilation readiness: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public Map<String, Object> autoTriggerCompilation(String templateId, String userId, String triggeredBy) throws ExecutionException, InterruptedException {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (!isReadyForCompilation(templateId, userId)) {
                result.put("success", false);
                result.put("message", "Not ready for compilation");
                return result;
            }
            
            // Get approved scenes
            List<SceneSubmission> approvedScenes = sceneSubmissionDao.getApprovedScenesInOrder(templateId, userId);
            List<String> sceneIds = approvedScenes.stream()
                .map(SceneSubmission::getId)
                .collect(Collectors.toList());
            
            // Trigger compilation
            boolean compilationStarted = false;
            if (videoCompilationService != null) {
                compilationStarted = videoCompilationService.triggerCompilation(templateId, userId, sceneIds, triggeredBy);
            } else {
                // Mock compilation without service
                CompiledVideo compiledVideo = new CompiledVideo(templateId, userId, sceneIds);
                compiledVideo.setCompiledBy(triggeredBy);
                compiledVideo.setStatus("compiling");
                compiledVideoDao.save(compiledVideo);
                compilationStarted = true;
            }
            
            if (compilationStarted) {
                // Send notifications
                Map<String, Object> notificationData = new HashMap<>();
                notificationData.put("templateId", templateId);
                notificationData.put("userId", userId);
                notificationData.put("sceneCount", approvedScenes.size());
                notificationData.put("triggeredBy", triggeredBy);
                
                sendWorkflowNotifications("compilation_started", notificationData);
                
                result.put("success", true);
                result.put("message", "Compilation triggered automatically");
                result.put("sceneCount", approvedScenes.size());
                
                System.out.println("Auto-triggered compilation for template " + templateId + " user " + userId);
                
            } else {
                result.put("success", false);
                result.put("message", "Failed to start compilation");
            }
            
        } catch (Exception e) {
            System.err.println("Error auto-triggering compilation: " + e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public Map<String, Object> processSceneApproval(String sceneId, String reviewerId) throws ExecutionException, InterruptedException {
        Map<String, Object> result = new HashMap<>();
        List<String> actionsPerformed = new ArrayList<>();
        
        try {
            SceneSubmission scene = sceneSubmissionDao.findById(sceneId);
            if (scene == null) {
                result.put("success", false);
                result.put("message", "Scene not found");
                return result;
            }
            
            // Send approval notification to user
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("sceneSubmission", scene);
            notificationData.put("reviewerId", reviewerId);
            notificationData.put("action", "approved");
            
            sendWorkflowNotifications("scene_approved", notificationData);
            actionsPerformed.add("Sent approval notification to content creator");
            
            // Check if compilation should be triggered
            Map<String, Object> compilationResult = autoTriggerCompilation(
                scene.getTemplateId(), scene.getUserId(), reviewerId);
                
            if ((Boolean) compilationResult.getOrDefault("success", false)) {
                actionsPerformed.add("Auto-triggered video compilation");
                result.put("compilationTriggered", true);
            } else {
                result.put("compilationTriggered", false);
                
                // Send progress update
                Map<String, Object> progressData = getWorkflowStatus(scene.getTemplateId(), scene.getUserId());
                if (progressData.containsKey("progress")) {
                    Map<String, Object> progress = (Map<String, Object>) progressData.get("progress");
                    int approved = (Integer) progress.getOrDefault("approved", 0);
                    int total = (Integer) progress.getOrDefault("totalScenes", 0);
                    
                    if (approved < total) {
                        notificationData.put("progress", progress);
                        sendWorkflowNotifications("progress_update", notificationData);
                        actionsPerformed.add("Sent progress update to content creator");
                    }
                }
            }
            
            result.put("success", true);
            result.put("actionsPerformed", actionsPerformed);
            
        } catch (Exception e) {
            System.err.println("Error processing scene approval: " + e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public Map<String, Object> processSceneRejection(String sceneId, String reviewerId, List<String> feedback) throws ExecutionException, InterruptedException {
        Map<String, Object> result = new HashMap<>();
        List<String> actionsPerformed = new ArrayList<>();
        
        try {
            SceneSubmission scene = sceneSubmissionDao.findById(sceneId);
            if (scene == null) {
                result.put("success", false);
                result.put("message", "Scene not found");
                return result;
            }
            
            // Send rejection notification with feedback
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("sceneSubmission", scene);
            notificationData.put("reviewerId", reviewerId);
            notificationData.put("feedback", feedback);
            notificationData.put("action", "rejected");
            
            sendWorkflowNotifications("scene_rejected", notificationData);
            actionsPerformed.add("Sent rejection notification with feedback to content creator");
            
            // Check if this is a repeated rejection
            if (scene.getResubmissionCount() > 2) {
                // Escalate to senior manager
                notificationData.put("escalation", true);
                sendWorkflowNotifications("scene_escalation", notificationData);
                actionsPerformed.add("Escalated repeated rejection to senior management");
            }
            
            result.put("success", true);
            result.put("actionsPerformed", actionsPerformed);
            
        } catch (Exception e) {
            System.err.println("Error processing scene rejection: " + e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public Map<String, Object> processPendingSubmissions() throws ExecutionException, InterruptedException {
        Map<String, Object> result = new HashMap<>();
        List<String> actionsPerformed = new ArrayList<>();
        
        try {
            List<SceneSubmission> pendingSubmissions = sceneSubmissionDao.findPendingSubmissionsForReview();
            
            // Group by priority (based on similarity score and submission time)
            List<SceneSubmission> highPriority = new ArrayList<>();
            List<SceneSubmission> normalPriority = new ArrayList<>();
            List<SceneSubmission> overdue = new ArrayList<>();
            
            Date now = new Date();
            long oneDayMs = 24 * 60 * 60 * 1000;
            
            for (SceneSubmission submission : pendingSubmissions) {
                long waitTime = now.getTime() - submission.getSubmittedAt().getTime();
                
                if (waitTime > oneDayMs) {
                    overdue.add(submission);
                } else if (submission.getSimilarityScore() != null && submission.getSimilarityScore() >= 0.9) {
                    highPriority.add(submission);
                } else {
                    normalPriority.add(submission);
                }
            }
            
            // Send notifications for overdue submissions
            if (!overdue.isEmpty()) {
                Map<String, Object> notificationData = new HashMap<>();
                notificationData.put("overdueSubmissions", overdue);
                notificationData.put("count", overdue.size());
                
                sendWorkflowNotifications("submissions_overdue", notificationData);
                actionsPerformed.add("Sent overdue notification for " + overdue.size() + " submissions");
            }
            
            // Send daily summary to managers
            if (!pendingSubmissions.isEmpty()) {
                Map<String, Object> summaryData = new HashMap<>();
                summaryData.put("totalPending", pendingSubmissions.size());
                summaryData.put("highPriority", highPriority.size());
                summaryData.put("overdue", overdue.size());
                summaryData.put("normal", normalPriority.size());
                
                sendWorkflowNotifications("daily_summary", summaryData);
                actionsPerformed.add("Sent daily summary to content managers");
            }
            
            result.put("success", true);
            result.put("totalPending", pendingSubmissions.size());
            result.put("highPriority", highPriority.size());
            result.put("overdue", overdue.size());
            result.put("actionsPerformed", actionsPerformed);
            
        } catch (Exception e) {
            System.err.println("Error processing pending submissions: " + e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public Map<String, Object> sendWorkflowNotifications(String event, Map<String, Object> data) throws ExecutionException, InterruptedException {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (notificationService != null) {
                // Use actual notification service
                return notificationService.sendNotification(event, data);
            } else {
                // Mock notification sending
                System.out.println("NOTIFICATION [" + event + "]: " + data.toString());
                
                result.put("success", true);
                result.put("event", event);
                result.put("notificationsSent", 1);
                result.put("method", "mock");
            }
            
        } catch (Exception e) {
            System.err.println("Error sending workflow notifications: " + e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public Map<String, Object> generateWorkflowAnalytics(String templateId, int days) throws ExecutionException, InterruptedException {
        Map<String, Object> analytics = new HashMap<>();
        
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, -days);
            Date startDate = calendar.getTime();
            Date endDate = new Date();
            
            // Get submissions in date range
            List<SceneSubmission> submissions = sceneSubmissionDao.findSubmissionsByDateRange(startDate, endDate);
            
            if (templateId != null) {
                submissions = submissions.stream()
                    .filter(s -> templateId.equals(s.getTemplateId()))
                    .collect(Collectors.toList());
            }
            
            // Calculate metrics
            int totalSubmissions = submissions.size();
            int approved = (int) submissions.stream().filter(s -> "approved".equals(s.getStatus())).count();
            int rejected = (int) submissions.stream().filter(s -> "rejected".equals(s.getStatus())).count();
            int pending = (int) submissions.stream().filter(s -> "pending".equals(s.getStatus())).count();
            
            // Calculate average processing time
            double avgProcessingTime = submissions.stream()
                .filter(s -> s.getReviewedAt() != null)
                .mapToLong(s -> s.getReviewedAt().getTime() - s.getSubmittedAt().getTime())
                .average()
                .orElse(0.0) / (1000 * 60 * 60); // Convert to hours
            
            // Calculate average similarity score
            double avgSimilarity = submissions.stream()
                .filter(s -> s.getSimilarityScore() != null)
                .mapToDouble(SceneSubmission::getSimilarityScore)
                .average()
                .orElse(0.0);
            
            // Resubmission rate
            long resubmissions = submissions.stream()
                .filter(s -> s.getResubmissionCount() > 0)
                .count();
            double resubmissionRate = totalSubmissions > 0 ? (double) resubmissions / totalSubmissions : 0.0;
            
            analytics.put("period", days + " days");
            analytics.put("templateId", templateId);
            analytics.put("totalSubmissions", totalSubmissions);
            analytics.put("approved", approved);
            analytics.put("rejected", rejected);
            analytics.put("pending", pending);
            analytics.put("approvalRate", totalSubmissions > 0 ? (double) approved / totalSubmissions : 0.0);
            analytics.put("rejectionRate", totalSubmissions > 0 ? (double) rejected / totalSubmissions : 0.0);
            analytics.put("avgProcessingTimeHours", avgProcessingTime);
            analytics.put("avgSimilarityScore", avgSimilarity);
            analytics.put("resubmissionRate", resubmissionRate);
            analytics.put("generatedAt", new Date());
            
        } catch (Exception e) {
            System.err.println("Error generating workflow analytics: " + e.getMessage());
            analytics.put("error", e.getMessage());
        }
        
        return analytics;
    }
    
    @Override
    public Map<String, Object> cleanupOldWorkflowData(int daysOld) throws ExecutionException, InterruptedException {
        Map<String, Object> result = new HashMap<>();
        int totalCleaned = 0;
        
        try {
            Calendar cutoff = Calendar.getInstance();
            cutoff.add(Calendar.DAY_OF_MONTH, -daysOld);
            Date cutoffDate = cutoff.getTime();
            
            // Clean up old failed compilations
            if (videoCompilationService != null) {
                totalCleaned += videoCompilationService.cleanupOldCompilations(daysOld);
            }
            
            // Clean up old rejected submissions (optional - keep for analytics)
            // Could add logic here to archive very old rejected submissions
            
            result.put("success", true);
            result.put("totalCleaned", totalCleaned);
            result.put("cutoffDate", cutoffDate);
            
        } catch (Exception e) {
            System.err.println("Error cleaning up workflow data: " + e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public Map<String, Object> getWorkflowStatus(String templateId, String userId) throws ExecutionException, InterruptedException {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // Get template info
            ManualTemplate template = templateDao.getTemplate(templateId);
            if (template == null) {
                status.put("error", "Template not found");
                return status;
            }
            
            int totalScenes = template.getScenes().size();
            
            // Get user's submissions
            List<SceneSubmission> submissions = sceneSubmissionDao.findByTemplateIdAndUserId(templateId, userId);
            
            int approved = (int) submissions.stream().filter(s -> "approved".equals(s.getStatus())).count();
            int pending = (int) submissions.stream().filter(s -> "pending".equals(s.getStatus())).count();
            int rejected = (int) submissions.stream().filter(s -> "rejected".equals(s.getStatus())).count();
            int submitted = submissions.size();
            
            // Calculate progress
            Map<String, Object> progress = new HashMap<>();
            progress.put("totalScenes", totalScenes);
            progress.put("submitted", submitted);
            progress.put("approved", approved);
            progress.put("pending", pending);
            progress.put("rejected", rejected);
            progress.put("remaining", Math.max(0, totalScenes - submitted));
            progress.put("completionPercentage", totalScenes > 0 ? (double) approved / totalScenes * 100 : 0);
            progress.put("isComplete", approved == totalScenes && totalScenes > 0);
            
            // Check compilation status
            CompiledVideo compilation = compiledVideoDao.findByTemplateIdAndUserId(templateId, userId);
            
            String workflowStage;
            if (compilation != null && "completed".equals(compilation.getStatus())) {
                workflowStage = "completed";
            } else if (compilation != null && "compiling".equals(compilation.getStatus())) {
                workflowStage = "compiling";
            } else if (approved == totalScenes && totalScenes > 0) {
                workflowStage = "ready_for_compilation";
            } else if (submitted > 0) {
                workflowStage = "in_progress";
            } else {
                workflowStage = "not_started";
            }
            
            status.put("templateId", templateId);
            status.put("userId", userId);
            status.put("workflowStage", workflowStage);
            status.put("progress", progress);
            status.put("lastUpdated", new Date());
            
            if (compilation != null) {
                status.put("compilation", Map.of(
                    "id", compilation.getId(),
                    "status", compilation.getStatus(),
                    "videoUrl", compilation.getVideoUrl()
                ));
            }
            
        } catch (Exception e) {
            System.err.println("Error getting workflow status: " + e.getMessage());
            status.put("error", e.getMessage());
        }
        
        return status;
    }
}