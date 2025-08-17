package com.example.demo.dao;

import com.example.demo.model.CompiledVideo;
import java.util.concurrent.ExecutionException;

/**
 * Simple compiled video DAO
 */
public interface CompiledVideoDao {
    
    String save(CompiledVideo compiledVideo) throws ExecutionException, InterruptedException;
    CompiledVideo findById(String id) throws ExecutionException, InterruptedException;
    CompiledVideo findByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException;
    
    // Count methods for dashboard
    int getCompletedVideoCountByUser(String userId) throws ExecutionException, InterruptedException;
    int getPublishedVideoCountByUser(String userId) throws ExecutionException, InterruptedException;
}