package com.example.demo.ai.providers.llm;

import com.example.demo.model.Video;
import java.util.List;
import java.util.Map;

public interface VideoSummaryService {
    String generateSummary(Video video, List<String> sceneLabels, Map<String, String> allBlockDescriptions);
    
    String generateSummary(Video video, List<String> sceneLabels, Map<String, String> allBlockDescriptions, String language);
}