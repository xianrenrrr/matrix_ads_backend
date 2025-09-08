package com.example.demo.ai.providers.llm;

import com.example.demo.model.Video;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class VideoSummaryServiceImpl implements VideoSummaryService {
    
    // Removed orchestrator dependency for dev

    
    @Override
    public String generateSummary(Video video, List<String> sceneLabels, Map<String, String> allBlockDescriptions, String language, String userDescription) {
        System.out.printf("Generating fallback video summary for: %s (description: %s)\n", 
                         video.getTitle(), userDescription != null ? "provided" : "none");
        
        // Dev: return fallback summary without orchestrator
        return getFallbackSummary(video, sceneLabels);
    }
    
    private String getFallbackSummary(Video video, List<String> sceneLabels) {
        return String.format("AI-generated template for '%s' with %d detected scenes", 
                            video.getTitle(), sceneLabels.size());
    }
    
}
