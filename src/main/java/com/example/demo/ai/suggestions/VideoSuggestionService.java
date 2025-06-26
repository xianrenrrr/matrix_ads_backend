package com.example.demo.ai.suggestions;

import com.example.demo.model.Video;
import com.example.demo.model.ManualTemplate;
import java.util.List;

/**
 * Task 3: AI Video Suggestions Service
 * 
 * This service will provide AI-powered suggestions and recommendations:
 * - Suggest improvements for video content
 * - Recommend optimal scenes and compositions
 * - Generate creative suggestions based on video analysis
 * 
 * Can utilize shared services:
 * - BlockDescriptionService (for analyzing visual content)
 * - VideoSummaryService (for understanding video context)
 * - KeyframeExtractionService (for extracting representative frames)
 */
public interface VideoSuggestionService {
    
    /**
     * Analyze video and suggest improvements
     */
    List<String> suggestImprovements(Video video);
    
    /**
     * Suggest optimal templates based on video content
     */
    List<ManualTemplate> suggestTemplates(Video video);
    
    /**
     * Generate creative suggestions for video enhancement
     */
    String generateCreativeSuggestions(Video video);
}