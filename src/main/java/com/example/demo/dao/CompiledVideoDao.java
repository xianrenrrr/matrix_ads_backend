package com.example.demo.dao;

import com.example.demo.model.CompiledVideo;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Data Access Object interface for CompiledVideo operations
 * Handles all database interactions for compiled videos created from approved scenes
 */
public interface CompiledVideoDao {
    
    // Basic CRUD Operations
    String save(CompiledVideo compiledVideo) throws ExecutionException, InterruptedException;
    CompiledVideo findById(String id) throws ExecutionException, InterruptedException;
    void update(CompiledVideo compiledVideo) throws ExecutionException, InterruptedException;
    void delete(String id) throws ExecutionException, InterruptedException;
    
    // Query Methods
    List<CompiledVideo> findByTemplateId(String templateId) throws ExecutionException, InterruptedException;
    List<CompiledVideo> findByUserId(String userId) throws ExecutionException, InterruptedException;
    CompiledVideo findByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException;
    List<CompiledVideo> findByStatus(String status) throws ExecutionException, InterruptedException;
    
    // Compilation Status Tracking
    List<CompiledVideo> findCompiling() throws ExecutionException, InterruptedException;
    List<CompiledVideo> findCompleted() throws ExecutionException, InterruptedException;
    List<CompiledVideo> findFailed() throws ExecutionException, InterruptedException;
    List<CompiledVideo> findFailedRetryable() throws ExecutionException, InterruptedException;
    
    // Job Management
    CompiledVideo findByCompilationJobId(String jobId) throws ExecutionException, InterruptedException;
    List<CompiledVideo> findByCompiledBy(String compiledBy) throws ExecutionException, InterruptedException;
    
    // Analytics and Reporting
    List<CompiledVideo> findRecentCompilations(int limit) throws ExecutionException, InterruptedException;
    List<CompiledVideo> findByDateRange(java.util.Date startDate, java.util.Date endDate) throws ExecutionException, InterruptedException;
    double getAverageCompilationTime() throws ExecutionException, InterruptedException;
    int countCompilationsByStatus(String status) throws ExecutionException, InterruptedException;
    
    // Publishing Management
    List<CompiledVideo> findPublished() throws ExecutionException, InterruptedException;
    List<CompiledVideo> findReadyForPublishing() throws ExecutionException, InterruptedException;
    void updatePublishStatus(String id, String publishStatus) throws ExecutionException, InterruptedException;
    
    // Cleanup Operations
    void deleteByTemplateId(String templateId) throws ExecutionException, InterruptedException;
    void deleteByUserId(String userId) throws ExecutionException, InterruptedException;
    void deleteOldFailedCompilations(java.util.Date cutoffDate) throws ExecutionException, InterruptedException;
}