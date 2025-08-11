package com.example.demo.dao;

import com.example.demo.model.SceneSubmission;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Data Access Object interface for SceneSubmission operations
 * Handles all database interactions for scene-level submissions
 */
public interface SceneSubmissionDao {
    
    // Basic CRUD Operations
    String save(SceneSubmission sceneSubmission) throws ExecutionException, InterruptedException;
    SceneSubmission findById(String id) throws ExecutionException, InterruptedException;
    void update(SceneSubmission sceneSubmission) throws ExecutionException, InterruptedException;
    void delete(String id) throws ExecutionException, InterruptedException;
    
    // Query Methods for Scene Management
    List<SceneSubmission> findByTemplateId(String templateId) throws ExecutionException, InterruptedException;
    List<SceneSubmission> findByUserId(String userId) throws ExecutionException, InterruptedException;
    List<SceneSubmission> findByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException;
    List<SceneSubmission> findByStatus(String status) throws ExecutionException, InterruptedException;
    List<SceneSubmission> findByTemplateIdAndStatus(String templateId, String status) throws ExecutionException, InterruptedException;
    
    // Scene-Specific Queries
    SceneSubmission findByTemplateIdAndUserIdAndSceneNumber(String templateId, String userId, int sceneNumber) throws ExecutionException, InterruptedException;
    List<SceneSubmission> findApprovedScenesByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException;
    List<SceneSubmission> findPendingScenesByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException;
    List<SceneSubmission> findRejectedScenesByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException;
    
    // Progress Tracking
    int countScenesByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException;
    int countApprovedScenesByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException;
    int countPendingScenesByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException;
    int countRejectedScenesByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException;
    
    // Manager Review Queries
    List<SceneSubmission> findPendingSubmissionsForReview() throws ExecutionException, InterruptedException;
    List<SceneSubmission> findSubmissionsByReviewer(String reviewerId) throws ExecutionException, InterruptedException;
    List<SceneSubmission> findRecentSubmissions(int limit) throws ExecutionException, InterruptedException;
    
    // Resubmission Tracking
    List<SceneSubmission> findResubmissionHistory(String originalSceneId) throws ExecutionException, InterruptedException;
    SceneSubmission findLatestSubmissionForScene(String templateId, String userId, int sceneNumber) throws ExecutionException, InterruptedException;
    
    // Compilation Readiness
    boolean areAllScenesApproved(String templateId, String userId, int totalScenes) throws ExecutionException, InterruptedException;
    List<SceneSubmission> getApprovedScenesInOrder(String templateId, String userId) throws ExecutionException, InterruptedException;
    
    // Analytics and Reporting
    List<SceneSubmission> findSubmissionsByDateRange(java.util.Date startDate, java.util.Date endDate) throws ExecutionException, InterruptedException;
    double getAverageSimilarityScore(String templateId) throws ExecutionException, InterruptedException;
    List<SceneSubmission> findTopPerformingScenes(String templateId, int limit) throws ExecutionException, InterruptedException;
    
    // Bulk Operations
    void deleteScenesByTemplateId(String templateId) throws ExecutionException, InterruptedException;
    void deleteScenesByUserId(String userId) throws ExecutionException, InterruptedException;
}