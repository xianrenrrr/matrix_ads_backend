package com.example.demo.service;

import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Notification Service Interface
 * Handles sending notifications for workflow events
 */
public interface NotificationService {
    
    /**
     * Send notification for a workflow event
     * @param event Event type
     * @param data Event data
     * @return Notification result
     */
    Map<String, Object> sendNotification(String event, Map<String, Object> data) throws ExecutionException, InterruptedException;
    
    /**
     * Send email notification
     * @param to Recipient email
     * @param subject Email subject
     * @param body Email body
     * @return Send result
     */
    Map<String, Object> sendEmail(String to, String subject, String body);
    
    /**
     * Send push notification to mini app
     * @param userId User ID
     * @param title Notification title
     * @param message Notification message
     * @return Send result
     */
    Map<String, Object> sendPushNotification(String userId, String title, String message);
    
    /**
     * Send in-app notification
     * @param userId User ID
     * @param type Notification type
     * @param data Notification data
     * @return Send result
     */
    Map<String, Object> sendInAppNotification(String userId, String type, Map<String, Object> data) throws ExecutionException, InterruptedException;
    
    /**
     * Notify manager that a template assignment has expired
     * @param managerId Manager user ID
     * @param groupId Group ID
     * @param templateTitle Template title
     */
    default void notifyTemplateExpired(String managerId, String groupId, String templateTitle) {
        // Default implementation - can be overridden
        try {
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("type", "template_expired");
            data.put("groupId", groupId);
            data.put("templateTitle", templateTitle);
            sendInAppNotification(managerId, "template_expired", data);
        } catch (Exception e) {
            // Ignore notification errors
        }
    }
    
    /**
     * Notify manager that a template assignment is expiring soon
     * @param managerId Manager user ID
     * @param groupId Group ID
     * @param templateTitle Template title
     * @param daysLeft Days until expiration
     */
    default void notifyTemplateExpiringSoon(String managerId, String groupId, String templateTitle, long daysLeft) {
        // Default implementation - can be overridden
        try {
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("type", "template_expiring_soon");
            data.put("groupId", groupId);
            data.put("templateTitle", templateTitle);
            data.put("daysLeft", daysLeft);
            sendInAppNotification(managerId, "template_expiring_soon", data);
        } catch (Exception e) {
            // Ignore notification errors
        }
    }
}
