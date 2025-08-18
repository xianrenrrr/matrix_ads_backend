package com.example.demo.ai.providers.llm;

import com.example.demo.model.Video;
import com.example.demo.ai.services.AIOrchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class VideoSummaryServiceImpl implements VideoSummaryService {
    
    @Autowired
    private AIOrchestrator aiOrchestrator;

    @Override
    public String generateSummary(Video video, List<String> sceneLabels, Map<String, String> allBlockDescriptions) {
        return generateSummary(video, sceneLabels, allBlockDescriptions, "en");
    }
    
    public String generateSummary(Video video, List<String> sceneLabels, Map<String, String> allBlockDescriptions, String language) {
        System.out.printf("Generating video summary for: %s using AI orchestrator%n", video.getTitle());
        
        try {
            // Use AI orchestrator to get the best available LLM (Qwen -> OpenAI)
            var response = aiOrchestrator.<LLMProvider.VideoSummary>executeWithFallback(
                com.example.demo.ai.core.AIModelType.LLM, 
                "generateVideoSummary",
                provider -> {
                    var llmProvider = (com.example.demo.ai.providers.llm.LLMProvider) provider;
                    
                    // Create proper request object
                    var request = new LLMProvider.VideoSummaryRequest();
                    request.setVideoTitle(video.getTitle());
                    request.setSceneLabels(sceneLabels);
                    request.setSceneDescriptions(allBlockDescriptions);
                    request.setLanguage(language);
                    
                    return llmProvider.generateVideoSummary(request);
                }
            );
            
            if (response.isSuccess() && response.getData() != null) {
                var videoSummary = response.getData();
                String aiSummary = videoSummary.getVideoSummaryZh(); // Get Chinese summary
                System.out.printf("Generated summary using %s: %s%n", response.getModelUsed(), aiSummary);
                return aiSummary;
            } else {
                System.err.println("AI orchestrator failed to generate summary. Using fallback.");
                return getFallbackSummary(video, sceneLabels);
            }
            
        } catch (Exception e) {
            System.err.printf("Error generating video summary: %s%n", e.getMessage());
            return getFallbackSummary(video, sceneLabels);
        }
    }
    
    private String getFallbackSummary(Video video, List<String> sceneLabels) {
        return String.format("AI-generated template for '%s' with %d detected scenes", 
                            video.getTitle(), sceneLabels.size());
    }
    
}