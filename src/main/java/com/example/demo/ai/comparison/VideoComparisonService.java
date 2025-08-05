package com.example.demo.ai.comparison;

import com.example.demo.model.ManualTemplate;
import java.util.List;
import java.util.Map;

/**
 * Video Comparison Service
 * 
 * This service handles AI-powered video comparison using semantic embeddings:
 * - Compare template videos with user-submitted videos
 * - Calculate similarity scores at block, scene, and overall levels
 * - Generate detailed comparison reports
 */
public interface VideoComparisonService {
    
    /**
     * Compare a user video against a reference template using processed scenes
     * @param templateScenes List of template scenes with block descriptions
     * @param userScenes List of user video scenes with block descriptions
     * @return Comprehensive comparison result
     */
    ComparisonResult compareVideoToTemplate(
        List<Map<String, String>> templateScenes, 
        List<Map<String, String>> userScenes
    );
    
    /**
     * Compare two processed templates directly
     * @param template1 First template with scene descriptions
     * @param template2 Second template with scene descriptions  
     * @return Similarity score between templates
     */
    ComparisonResult compareTemplates(ManualTemplate template1, ManualTemplate template2);
    
    /**
     * Generate a detailed comparison report in text format
     * @param result Comparison result from compareVideoToTemplate
     * @return Human-readable comparison report
     */
    String generateComparisonReport(ComparisonResult result);
}