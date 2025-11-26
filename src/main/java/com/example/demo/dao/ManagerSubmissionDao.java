package com.example.demo.dao;

import java.util.List;
import java.util.Map;

/**
 * DAO for managing pre-aggregated submissions under manager's collection
 * Structure: managerSubmissions/{managerId}/submissions/{submissionId}
 * 
 * This provides fast access to submissions without needing to query
 * and enrich data on every request.
 */
public interface ManagerSubmissionDao {
    
    /**
     * Save or update a submission under manager's collection
     */
    void saveSubmission(String managerId, Map<String, Object> submission);
    
    /**
     * Get all submissions for a manager
     */
    List<Map<String, Object>> getSubmissions(String managerId);
    
    /**
     * Update submission status
     */
    void updateSubmissionStatus(String managerId, String submissionId, String status);
    
    /**
     * Delete a specific submission
     */
    void deleteSubmission(String managerId, String submissionId);
    
    /**
     * Delete all submissions for an assignment (when assignment is deleted/expired)
     */
    void deleteByAssignmentId(String managerId, String assignmentId);
    
    /**
     * Get a specific submission
     */
    Map<String, Object> getSubmission(String managerId, String submissionId);
}
