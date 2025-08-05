package com.example.demo.service;

import com.example.demo.model.SceneSubmission;
import com.example.demo.model.CompiledVideo;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Workflow Automation Service Interface
 * Handles automated workflows for scene submissions and video compilation
 */
public interface WorkflowAutomationService {
    
    /**
     * Process scene submission through automated workflow
     * @param sceneSubmission The scene submission to process
     * @return Workflow result with next actions
     */
    Map<String, Object> processSceneSubmission(SceneSubmission sceneSubmission) throws ExecutionException, InterruptedException;
    
    /**
     * Check if all scenes are ready for compilation
     * @param templateId Template ID
     * @param userId User ID
     * @return true if compilation can be triggered
     */
    boolean isReadyForCompilation(String templateId, String userId) throws ExecutionException, InterruptedException;
    
    /**
     * Auto-trigger compilation when all scenes are approved
     * @param templateId Template ID
     * @param userId User ID
     * @param triggeredBy ID of who triggered the final approval
     * @return Compilation job details if triggered
     */
    Map<String, Object> autoTriggerCompilation(String templateId, String userId, String triggeredBy) throws ExecutionException, InterruptedException;
    
    /**
     * Process scene approval and check for compilation triggers
     * @param sceneId Scene submission ID
     * @param reviewerId Reviewer ID
     * @return Workflow result with actions taken
     */
    Map<String, Object> processSceneApproval(String sceneId, String reviewerId) throws ExecutionException, InterruptedException;
    
    /**
     * Process scene rejection and trigger notifications
     * @param sceneId Scene submission ID
     * @param reviewerId Reviewer ID
     * @param feedback Rejection feedback
     * @return Workflow result with notifications sent
     */
    Map<String, Object> processSceneRejection(String sceneId, String reviewerId, List<String> feedback) throws ExecutionException, InterruptedException;
    
    /**
     * Monitor and process pending submissions
     * @return Processing results
     */
    Map<String, Object> processPendingSubmissions() throws ExecutionException, InterruptedException;
    
    /**
     * Send notifications based on workflow events
     * @param event Workflow event type
     * @param data Event data
     * @return Notification results
     */
    Map<String, Object> sendWorkflowNotifications(String event, Map<String, Object> data) throws ExecutionException, InterruptedException;
    
    /**
     * Generate workflow analytics and insights
     * @param templateId Optional template ID filter
     * @param days Number of days to analyze
     * @return Analytics data
     */
    Map<String, Object> generateWorkflowAnalytics(String templateId, int days) throws ExecutionException, InterruptedException;
    
    /**
     * Cleanup old workflow data
     * @param daysOld Delete data older than this many days
     * @return Cleanup results
     */
    Map<String, Object> cleanupOldWorkflowData(int daysOld) throws ExecutionException, InterruptedException;
    
    /**
     * Get workflow status for a specific submission
     * @param templateId Template ID
     * @param userId User ID
     * @return Current workflow status
     */
    Map<String, Object> getWorkflowStatus(String templateId, String userId) throws ExecutionException, InterruptedException;
}