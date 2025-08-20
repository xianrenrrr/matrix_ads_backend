package com.example.demo.ai.providers.llm;

import com.example.demo.model.Video;
import java.util.List;
import java.util.Map;

public interface VideoSummaryService {
    default String generateSummary(Video video, List<String> sceneLabels, Map<String, String> allBlockDescriptions) {
        return generateSummary(video, sceneLabels, allBlockDescriptions, "en", null);
    }
    
    default String generateSummary(Video video, List<String> sceneLabels, Map<String, String> allBlockDescriptions, String language) {
        return generateSummary(video, sceneLabels, allBlockDescriptions, language, null);
    }
    
    String generateSummary(Video video, List<String> sceneLabels, Map<String, String> allBlockDescriptions, String language, String userDescription);
}