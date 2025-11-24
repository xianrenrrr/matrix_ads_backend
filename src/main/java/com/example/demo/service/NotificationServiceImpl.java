package com.example.demo.service;

import com.example.demo.dao.UserDao;
import com.example.demo.model.User;
import com.example.demo.model.SceneSubmission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Notification Service Implementation
 * Handles sending notifications to users about workflow events
 */
@Service
public class NotificationServiceImpl implements NotificationService {
    
    @Autowired
    private UserDao userDao;
    
    @Override
    public Map<String, Object> sendNotification(String event, Map<String, Object> data) throws ExecutionException, InterruptedException {
        Map<String, Object> result = new HashMap<>();
        List<String> notificationsSent = new ArrayList<>();
        
        try {
            switch (event) {
                case "scene_submitted":
                    notificationsSent.addAll(handleSceneSubmitted(data));
                    break;
                    
                case "scene_approved":
                    notificationsSent.addAll(handleSceneApproved(data));
                    break;
                    
                case "scene_rejected":
                    notificationsSent.addAll(handleSceneRejected(data));
                    break;
                    
                case "compilation_started":
                    notificationsSent.addAll(handleCompilationStarted(data));
                    break;
                    
                case "compilation_completed":
                    notificationsSent.addAll(handleCompilationCompleted(data));
                    break;
                    
                case "progress_update":
                    notificationsSent.addAll(handleProgressUpdate(data));
                    break;
                    
                case "submissions_overdue":
                    notificationsSent.addAll(handleOverdueSubmissions(data));
                    break;
                    
                case "daily_summary":
                    notificationsSent.addAll(handleDailySummary(data));
                    break;
                    
                case "scene_escalation":
                    notificationsSent.addAll(handleSceneEscalation(data));
                    break;
                    
                default:
                    System.out.println("Unknown notification event: " + event);
            }
            
            result.put("success", true);
            result.put("event", event);
            result.put("notificationsSent", notificationsSent.size());
            result.put("recipients", notificationsSent);
            
        } catch (Exception e) {
            System.err.println("Error sending notifications: " + e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    private List<String> handleSceneSubmitted(Map<String, Object> data) throws ExecutionException, InterruptedException {
        List<String> recipients = new ArrayList<>();
        SceneSubmission scene = (SceneSubmission) data.get("sceneSubmission");
        
        if (scene != null) {
            // Notify all content managers
            List<User> managers = userDao.findByRole("ContentManager");
            for (User manager : managers) {
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "scene_submitted");
                notification.put("message", String.format("New scene submission for review: Scene %d of template %s", 
                    scene.getSceneNumber(), scene.getTemplateId()));
                notification.put("sceneId", scene.getId());
                notification.put("templateId", scene.getTemplateId());
                notification.put("submittedBy", scene.getUserId());
                notification.put("timestamp", System.currentTimeMillis());
                notification.put("read", false);
                
                saveNotificationToUser(manager.getId(), notification);
                recipients.add(manager.getId());
            }
        }
        
        return recipients;
    }
    
    private List<String> handleSceneApproved(Map<String, Object> data) throws ExecutionException, InterruptedException {
        List<String> recipients = new ArrayList<>();
        SceneSubmission scene = (SceneSubmission) data.get("sceneSubmission");
        
        if (scene != null) {
            // Notify content creator
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "scene_approved");
            notification.put("message", String.format("Your scene %d has been approved!", scene.getSceneNumber()));
            notification.put("sceneId", scene.getId());
            notification.put("templateId", scene.getTemplateId());
            notification.put("reviewedBy", data.get("reviewerId"));
            notification.put("timestamp", System.currentTimeMillis());
            notification.put("read", false);
            
            saveNotificationToUser(scene.getUserId(), notification);
            recipients.add(scene.getUserId());
        }
        
        return recipients;
    }
    
    private List<String> handleSceneRejected(Map<String, Object> data) throws ExecutionException, InterruptedException {
        List<String> recipients = new ArrayList<>();
        SceneSubmission scene = (SceneSubmission) data.get("sceneSubmission");
        List<String> feedback = (List<String>) data.get("feedback");
        
        if (scene != null) {
            // Notify content creator with feedback
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "scene_rejected");
            notification.put("message", String.format("Scene %d needs revision. Please review feedback.", scene.getSceneNumber()));
            notification.put("sceneId", scene.getId());
            notification.put("templateId", scene.getTemplateId());
            notification.put("feedback", feedback);
            notification.put("reviewedBy", data.get("reviewerId"));
            notification.put("timestamp", System.currentTimeMillis());
            notification.put("read", false);
            
            saveNotificationToUser(scene.getUserId(), notification);
            recipients.add(scene.getUserId());
        }
        
        return recipients;
    }
    
    private List<String> handleCompilationStarted(Map<String, Object> data) throws ExecutionException, InterruptedException {
        List<String> recipients = new ArrayList<>();
        String userId = (String) data.get("userId");
        String templateId = (String) data.get("templateId");
        Integer sceneCount = (Integer) data.get("sceneCount");
        
        if (userId != null) {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "compilation_started");
            notification.put("message", String.format("All %d scenes approved! Video compilation has started.", sceneCount));
            notification.put("templateId", templateId);
            notification.put("timestamp", System.currentTimeMillis());
            notification.put("read", false);
            
            saveNotificationToUser(userId, notification);
            recipients.add(userId);
        }
        
        return recipients;
    }
    
    private List<String> handleCompilationCompleted(Map<String, Object> data) throws ExecutionException, InterruptedException {
        List<String> recipients = new ArrayList<>();
        String userId = (String) data.get("userId");
        String videoUrl = (String) data.get("videoUrl");
        String templateId = (String) data.get("templateId");
        
        if (userId != null) {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "compilation_completed");
            notification.put("message", "Your video has been compiled successfully! Ready for download.");
            notification.put("templateId", templateId);
            notification.put("videoUrl", videoUrl);
            notification.put("timestamp", System.currentTimeMillis());
            notification.put("read", false);
            
            saveNotificationToUser(userId, notification);
            recipients.add(userId);
        }
        
        return recipients;
    }
    
    private List<String> handleProgressUpdate(Map<String, Object> data) throws ExecutionException, InterruptedException {
        List<String> recipients = new ArrayList<>();
        SceneSubmission scene = (SceneSubmission) data.get("sceneSubmission");
        Map<String, Object> progress = (Map<String, Object>) data.get("progress");
        
        if (scene != null && progress != null) {
            int approved = (Integer) progress.getOrDefault("approved", 0);
            int total = (Integer) progress.getOrDefault("totalScenes", 0);
            
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "progress_update");
            notification.put("message", String.format("Progress: %d/%d scenes approved", approved, total));
            notification.put("templateId", scene.getTemplateId());
            notification.put("progress", progress);
            notification.put("timestamp", System.currentTimeMillis());
            notification.put("read", false);
            
            saveNotificationToUser(scene.getUserId(), notification);
            recipients.add(scene.getUserId());
        }
        
        return recipients;
    }
    
    private List<String> handleOverdueSubmissions(Map<String, Object> data) throws ExecutionException, InterruptedException {
        List<String> recipients = new ArrayList<>();
        List<SceneSubmission> overdueSubmissions = (List<SceneSubmission>) data.get("overdueSubmissions");
        Integer count = (Integer) data.get("count");
        
        // Notify all content managers
        List<User> managers = userDao.findByRole("ContentManager");
        for (User manager : managers) {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "submissions_overdue");
            notification.put("message", String.format("%d scene submissions are overdue for review", count));
            notification.put("overdueCount", count);
            notification.put("priority", "high");
            notification.put("timestamp", System.currentTimeMillis());
            notification.put("read", false);
            
            saveNotificationToUser(manager.getId(), notification);
            recipients.add(manager.getId());
        }
        
        return recipients;
    }
    
    private List<String> handleDailySummary(Map<String, Object> data) throws ExecutionException, InterruptedException {
        List<String> recipients = new ArrayList<>();
        
        // Notify all content managers with daily summary
        List<User> managers = userDao.findByRole("ContentManager");
        for (User manager : managers) {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "daily_summary");
            notification.put("message", String.format("Daily Summary: %d pending, %d high priority, %d overdue",
                data.getOrDefault("totalPending", 0),
                data.getOrDefault("highPriority", 0),
                data.getOrDefault("overdue", 0)));
            notification.put("summaryData", data);
            notification.put("timestamp", System.currentTimeMillis());
            notification.put("read", false);
            
            saveNotificationToUser(manager.getId(), notification);
            recipients.add(manager.getId());
        }
        
        return recipients;
    }
    
    private List<String> handleSceneEscalation(Map<String, Object> data) throws ExecutionException, InterruptedException {
        List<String> recipients = new ArrayList<>();
        SceneSubmission scene = (SceneSubmission) data.get("sceneSubmission");
        
        if (scene != null) {
            // Find senior managers or admins for escalation
            List<User> seniorManagers = userDao.findByRole("Admin");
            if (seniorManagers.isEmpty()) {
                seniorManagers = userDao.findByRole("ContentManager");
            }
            
            for (User manager : seniorManagers) {
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "scene_escalation");
                notification.put("message", String.format("ESCALATION: Scene repeatedly rejected (%d attempts) - needs attention",
                    scene.getResubmissionCount() + 1));
                notification.put("sceneId", scene.getId());
                notification.put("templateId", scene.getTemplateId());
                notification.put("userId", scene.getUserId());
                notification.put("priority", "urgent");
                notification.put("timestamp", System.currentTimeMillis());
                notification.put("read", false);
                
                saveNotificationToUser(manager.getId(), notification);
                recipients.add(manager.getId());
            }
        }
        
        return recipients;
    }
    
    private void saveNotificationToUser(String userId, Map<String, Object> notification) throws ExecutionException, InterruptedException {
        try {
            // Generate unique notification ID
            String notificationId = UUID.randomUUID().toString();
            
            // Save notification using DAO
            userDao.addNotification(userId, notificationId, notification);
            
            System.out.println("Notification saved for user " + userId + ": " + notification.get("message"));
            
        } catch (Exception e) {
            System.err.println("Error saving notification for user " + userId + ": " + e.getMessage());
        }
    }
    
    @Override
    public Map<String, Object> sendEmail(String to, String subject, String body) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Mock email sending - in production, integrate with email service
            System.out.println("EMAIL SENT - To: " + to + ", Subject: " + subject);
            result.put("success", true);
            result.put("method", "email");
            result.put("to", to);
            result.put("subject", subject);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    
    @Override
    public Map<String, Object> sendPushNotification(String userId, String title, String message) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Mock push notification - in production, integrate with push service
            System.out.println("PUSH NOTIFICATION - User: " + userId + ", Title: " + title + ", Message: " + message);
            result.put("success", true);
            result.put("method", "push");
            result.put("userId", userId);
            result.put("title", title);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    
    @Override
    public Map<String, Object> sendInAppNotification(String userId, String type, Map<String, Object> data) throws ExecutionException, InterruptedException {
        Map<String, Object> result = new HashMap<>();
        try {
            // Save in-app notification to user's notifications
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", type);
            notification.put("data", data);
            notification.put("timestamp", System.currentTimeMillis());
            notification.put("read", false);
            
            saveNotificationToUser(userId, notification);
            
            result.put("success", true);
            result.put("method", "in-app");
            result.put("userId", userId);
            result.put("type", type);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
}