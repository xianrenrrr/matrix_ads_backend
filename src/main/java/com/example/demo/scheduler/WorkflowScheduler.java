package com.example.demo.scheduler;

import com.example.demo.service.WorkflowAutomationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Workflow Scheduler
 * Handles scheduled tasks for workflow automation
 */
@Component
public class WorkflowScheduler {
    
    @Autowired
    private WorkflowAutomationService workflowAutomationService;
    
    /**
     * Process pending submissions every hour
     * Checks for overdue submissions and sends notifications
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void processPendingSubmissions() {
        try {
            System.out.println("Running scheduled task: Process pending submissions");
            Map<String, Object> result = workflowAutomationService.processPendingSubmissions();
            System.out.println("Pending submissions processed: " + result);
        } catch (Exception e) {
            System.err.println("Error in scheduled task (processPendingSubmissions): " + e.getMessage());
        }
    }
    
    /**
     * Send daily summary to managers
     * Runs every day at 9 AM
     */
    @Scheduled(cron = "0 0 9 * * *") // Every day at 9 AM
    public void sendDailySummary() {
        try {
            System.out.println("Running scheduled task: Send daily summary");
            
            // Generate analytics for the last 24 hours
            Map<String, Object> analytics = workflowAutomationService.generateWorkflowAnalytics(null, 1);
            
            // Send as daily summary notification
            Map<String, Object> notificationData = Map.of(
                "analytics", analytics,
                "type", "daily_summary"
            );
            
            Map<String, Object> result = workflowAutomationService.sendWorkflowNotifications("daily_summary", notificationData);
            System.out.println("Daily summary sent: " + result);
            
        } catch (Exception e) {
            System.err.println("Error in scheduled task (sendDailySummary): " + e.getMessage());
        }
    }
    
    /**
     * Clean up old workflow data
     * Runs every Sunday at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * SUN") // Every Sunday at 2 AM
    public void cleanupOldData() {
        try {
            System.out.println("Running scheduled task: Cleanup old workflow data");
            
            // Clean up data older than 90 days
            Map<String, Object> result = workflowAutomationService.cleanupOldWorkflowData(90);
            System.out.println("Cleanup completed: " + result);
            
        } catch (Exception e) {
            System.err.println("Error in scheduled task (cleanupOldData): " + e.getMessage());
        }
    }
    
    /**
     * Generate weekly analytics report
     * Runs every Monday at 8 AM
     */
    @Scheduled(cron = "0 0 8 * * MON") // Every Monday at 8 AM
    public void generateWeeklyReport() {
        try {
            System.out.println("Running scheduled task: Generate weekly analytics");
            
            // Generate analytics for the last 7 days
            Map<String, Object> analytics = workflowAutomationService.generateWorkflowAnalytics(null, 7);
            
            // Could extend this to save the report or email it
            System.out.println("Weekly analytics generated: " + analytics);
            
        } catch (Exception e) {
            System.err.println("Error in scheduled task (generateWeeklyReport): " + e.getMessage());
        }
    }
}