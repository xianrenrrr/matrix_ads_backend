package com.example.demo.dao;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public interface SubmittedVideoDao {
    
    /**
     * Get submitted video by composite ID (userId_templateId)
     */
    Map<String, Object> getSubmittedVideo(String compositeVideoId) throws ExecutionException, InterruptedException;
    
    /**
     * Get all submitted videos
     */
    List<Map<String, Object>> getAllSubmittedVideos() throws ExecutionException, InterruptedException;
    
    /**
     * Get submitted videos by user
     */
    List<Map<String, Object>> getSubmittedVideosByUser(String userId) throws ExecutionException, InterruptedException;
    
    /**
     * Get submitted videos by status
     */
    List<Map<String, Object>> getSubmittedVideosByStatus(String status) throws ExecutionException, InterruptedException;
    
    /**
     * Update submitted video
     */
    void updateSubmittedVideo(String compositeVideoId, Map<String, Object> updates) throws ExecutionException, InterruptedException;
    
    /**
     * Get video count by user
     */
    int getVideoCountByUser(String userId) throws ExecutionException, InterruptedException;
    
    /**
     * Get published video count by user
     */
    int getPublishedVideoCountByUser(String userId) throws ExecutionException, InterruptedException;
}