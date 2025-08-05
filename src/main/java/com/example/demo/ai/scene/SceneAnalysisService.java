package com.example.demo.ai.scene;

import com.example.demo.model.SceneSubmission;
import java.util.Map;
import java.util.List;

/**
 * Scene Analysis Service Interface
 * Provides AI-powered analysis for individual scene submissions
 */
public interface SceneAnalysisService {
    
    /**
     * Analyze a scene submission for quality metrics
     * @param sceneSubmission The scene submission to analyze
     * @return Quality metrics map with scores for various aspects
     */
    Map<String, Object> analyzeSceneQuality(SceneSubmission sceneSubmission);
    
    /**
     * Compare scene against template example
     * @param sceneSubmission User's scene submission
     * @param templateSceneData Template scene data for comparison
     * @return Similarity score between 0.0 and 1.0
     */
    double compareSceneToTemplate(SceneSubmission sceneSubmission, Map<String, Object> templateSceneData);
    
    /**
     * Generate AI suggestions for scene improvement
     * @param sceneSubmission The scene submission to analyze
     * @param similarityScore Current similarity score
     * @return List of improvement suggestions
     */
    List<String> generateSceneImprovementSuggestions(SceneSubmission sceneSubmission, double similarityScore);
    
    /**
     * Analyze scene composition and framing
     * @param videoUrl URL of the scene video
     * @param expectedFraming Expected framing from template
     * @return Composition analysis results
     */
    Map<String, Object> analyzeSceneComposition(String videoUrl, String expectedFraming);
    
    /**
     * Analyze scene audio quality
     * @param videoUrl URL of the scene video
     * @return Audio analysis results
     */
    Map<String, Object> analyzeSceneAudio(String videoUrl);
    
    /**
     * Analyze scene lighting and visual quality
     * @param videoUrl URL of the scene video
     * @return Lighting analysis results
     */
    Map<String, Object> analyzeSceneLighting(String videoUrl);
    
    /**
     * Extract key moments/frames from scene
     * @param videoUrl URL of the scene video
     * @param keyMomentCount Number of key moments to extract
     * @return List of key moment timestamps and descriptions
     */
    List<Map<String, Object>> extractSceneKeyMoments(String videoUrl, int keyMomentCount);
    
    /**
     * Analyze scene duration and pacing
     * @param sceneSubmission The scene submission to analyze
     * @param expectedDuration Expected duration from template
     * @return Duration analysis results
     */
    Map<String, Object> analyzeScenePacing(SceneSubmission sceneSubmission, double expectedDuration);
    
    /**
     * Generate comprehensive scene report
     * @param sceneSubmission The scene submission to analyze
     * @return Comprehensive analysis report
     */
    Map<String, Object> generateSceneAnalysisReport(SceneSubmission sceneSubmission);
}