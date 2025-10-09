package com.example.demo.ai.services;

import com.example.demo.model.Scene;
import com.example.demo.model.Video;

/**
 * Service for analyzing individual scene videos without scene detection.
 * Used for manual template creation where each uploaded video represents one complete scene.
 */
public interface SceneAnalysisService {
    /**
     * Analyze a single video as one complete scene (no scene detection/cutting).
     * 
     * @param video The video to analyze
     * @param language Language for AI analysis (e.g., "zh-CN", "en")
     * @param sceneDescription User-provided scene description (context for AI)
     * @return Scene with all AI-generated metadata (overlays, instructions, etc.)
     */
    Scene analyzeSingleScene(Video video, String language, String sceneDescription);
}
