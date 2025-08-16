package com.example.demo.service;

import com.example.demo.model.CompiledVideo;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Video Compilation Service Interface
 * Handles the compilation of approved scene submissions into final videos using FFmpeg
 */
public interface VideoCompilationService {
    
    /**
     * Trigger compilation of approved scenes into a final video
     * @param templateId Template ID
     * @param userId User ID who submitted the scenes
     * @param sceneSubmissionIds List of approved scene submission IDs in order
     * @param triggeredBy ID of manager who triggered compilation
     * @return true if compilation was triggered successfully
     */
    boolean triggerCompilation(String templateId, String userId, List<String> sceneSubmissionIds, String triggeredBy);
    
    /**
     * Check compilation status for a job
     * @param compilationJobId FFmpeg job ID
     * @return CompiledVideo with updated status
     */
    CompiledVideo checkCompilationStatus(String compilationJobId) throws ExecutionException, InterruptedException;
    
    /**
     * Retry a failed compilation
     * @param compiledVideoId ID of failed compilation
     * @return true if retry was initiated
     */
    boolean retryCompilation(String compiledVideoId) throws ExecutionException, InterruptedException;
    
    /**
     * Cancel an ongoing compilation
     * @param compilationJobId FFmpeg job ID to cancel
     * @return true if cancellation was successful
     */
    boolean cancelCompilation(String compilationJobId);
    
    /**
     * Get compilation progress for a job
     * @param compilationJobId FFmpeg job ID
     * @return Progress percentage (0-100)
     */
    double getCompilationProgress(String compilationJobId);
    
    /**
     * Clean up old failed compilations
     * @param daysOld Delete compilations older than this many days
     * @return Number of compilations cleaned up
     */
    int cleanupOldCompilations(int daysOld) throws ExecutionException, InterruptedException;
    
    /**
     * Generate video thumbnail from compiled video
     * @param videoUrl URL of the compiled video
     * @return URL of generated thumbnail
     */
    String generateThumbnail(String videoUrl);
    
    /**
     * Get estimated compilation time based on total duration
     * @param totalDurationSeconds Total duration of all scenes combined
     * @return Estimated compilation time in seconds
     */
    long getEstimatedCompilationTime(double totalDurationSeconds);
}