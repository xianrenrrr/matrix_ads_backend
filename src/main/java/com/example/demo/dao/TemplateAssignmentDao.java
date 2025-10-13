package com.example.demo.dao;

import com.example.demo.model.TemplateAssignment;
import java.util.List;

/**
 * Data Access Object for TemplateAssignment operations
 */
public interface TemplateAssignmentDao {
    
    /**
     * Create a new template assignment
     */
    String createAssignment(TemplateAssignment assignment) throws Exception;
    
    /**
     * Get assignment by ID
     */
    TemplateAssignment getAssignment(String assignmentId) throws Exception;
    
    /**
     * Get all assignments for a specific template
     */
    List<TemplateAssignment> getAssignmentsByTemplate(String templateId) throws Exception;
    
    /**
     * Get all assignments for a specific group
     */
    List<TemplateAssignment> getAssignmentsByGroup(String groupId) throws Exception;
    
    /**
     * Get all expired assignments
     */
    List<TemplateAssignment> getExpiredAssignments() throws Exception;
    
    /**
     * Get assignments expiring within specified days
     */
    List<TemplateAssignment> getExpiringSoonAssignments(int daysThreshold) throws Exception;
    
    /**
     * Update an existing assignment
     */
    void updateAssignment(TemplateAssignment assignment) throws Exception;
    
    /**
     * Delete an assignment
     */
    void deleteAssignment(String assignmentId) throws Exception;
    
    /**
     * Check if a group already has an active assignment for a template
     */
    boolean hasActiveAssignment(String templateId, String groupId) throws Exception;
}
