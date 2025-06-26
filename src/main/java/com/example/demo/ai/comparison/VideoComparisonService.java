package com.example.demo.ai.comparison;

import com.example.demo.model.Video;

/**
 * Task 2: Video Comparison Service
 * 
 * This service will handle AI-powered video comparison features:
 * - Compare two videos for similarity
 * - Analyze differences in content, style, and composition
 * - Generate comparison reports
 * 
 * Can utilize shared services:
 * - KeyframeExtractionService (for extracting frames to compare)
 * - BlockDescriptionService (for analyzing frame content)
 * - VideoSummaryService (for summarizing differences)
 */
public interface VideoComparisonService {
    
    /**
     * Compare two videos and return similarity score (0.0 - 1.0)
     */
    double compareVideos(Video video1, Video video2);
    
    /**
     * Generate detailed comparison report
     */
    String generateComparisonReport(Video video1, Video video2);
}