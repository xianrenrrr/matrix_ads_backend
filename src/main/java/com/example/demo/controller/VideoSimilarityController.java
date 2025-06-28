package com.example.demo.controller;

import com.example.demo.ai.EditSuggestionService;
import com.example.demo.ai.comparison.VideoComparisonIntegrationService;
import com.example.demo.ai.comparison.VideoComparisonService;
import com.example.demo.dao.VideoDao;
import com.example.demo.dao.TemplateDao;
import com.example.demo.model.Video;
import com.example.demo.model.ManualTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/video-similarity")
public class VideoSimilarityController {

    @Autowired
    private VideoComparisonIntegrationService videoComparisonService;
    
    @Autowired
    private EditSuggestionService editSuggestionService;
    
    @Autowired
    private VideoDao videoDao;
    
    @Autowired(required = false)
    private TemplateDao templateDao;

    /**
     * Get similarity analysis and edit suggestions for a video submission
     */
    @GetMapping("/analyze/{videoId}")
    public ResponseEntity<VideoAnalysisResponse> analyzeVideo(@PathVariable String videoId) {
        System.out.println("Analyze endpoint called with videoId: " + videoId);
        try {
            if (videoDao == null) {
                System.out.println("VideoDao is null, returning mock data");
                return ResponseEntity.ok(createMockAnalysis(videoId));
            }

            // Get submitted video details
            System.out.println("Getting submitted video details for ID: " + videoId);
            Video submittedVideo = videoDao.getVideoById(videoId);
            if (submittedVideo == null) {
                System.out.println("Submitted video not found, returning 404");
                return ResponseEntity.notFound().build();
            }
            System.out.println("Submitted video found with templateId: " + submittedVideo.getTemplateId());

            // Get template details
            ManualTemplate template = null;
            Video exampleVideo = null;
            if (submittedVideo.getTemplateId() != null && templateDao != null) {
                template = templateDao.getTemplate(submittedVideo.getTemplateId());
                if (template != null && template.getVideoId() != null) {
                    // Get the example video for this template
                    exampleVideo = videoDao.getVideoById(template.getVideoId());
                    System.out.println("Found example video: " + (exampleVideo != null ? exampleVideo.getId() : "null"));
                }
            }

            // Get similarity analysis comparing submitted video with example video
            List<Map<String, String>> userScenes = null;
            List<Map<String, String>> exampleScenes = null;
            
            if (exampleVideo != null) {
                System.out.println("Comparing submitted video URL: " + submittedVideo.getUrl());
                System.out.println("With example video URL: " + exampleVideo.getUrl());
                
                userScenes = videoComparisonService.getUserVideoScenesById(videoId);
                exampleScenes = videoComparisonService.getUserVideoScenesById(exampleVideo.getId());
            }

            // Calculate overall similarity between submitted and example video
            double overallSimilarity = calculateOverallSimilarity(userScenes, exampleScenes);

            // Generate edit suggestions based on comparison with example video
            List<String> suggestions = null;
            if (exampleScenes != null && !exampleScenes.isEmpty() && userScenes != null && !userScenes.isEmpty()) {
                EditSuggestionService.EditSuggestionRequest suggestionRequest = createSuggestionRequest(
                    exampleScenes.get(0), userScenes.get(0), overallSimilarity);
                EditSuggestionService.EditSuggestionResponse suggestionResponse = 
                    editSuggestionService.generateSuggestions(suggestionRequest);
                suggestions = suggestionResponse.getSuggestions();
            }

            VideoAnalysisResponse response = new VideoAnalysisResponse();
            response.videoId = videoId;
            response.overallSimilarity = overallSimilarity;
            response.templateTitle = template != null ? template.getTemplateTitle() : "Unknown Template";
            response.suggestions = suggestions != null ? suggestions : List.of("No suggestions available");
            response.detailedAnalysis = createDetailedAnalysis(userScenes, exampleScenes);

            return ResponseEntity.ok(response);

        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.ok(createMockAnalysis(videoId));
        } catch (Exception e) {
            return ResponseEntity.ok(createMockAnalysis(videoId));
        }
    }

    /**
     * Get suggestions only for a specific video
     */
    @GetMapping("/suggestions/{videoId}")
    public ResponseEntity<List<String>> getVideoSuggestions(@PathVariable String videoId) {
        try {
            VideoAnalysisResponse analysis = analyzeVideo(videoId).getBody();
            return ResponseEntity.ok(analysis != null ? analysis.suggestions : List.of("No suggestions available"));
        } catch (Exception e) {
            return ResponseEntity.ok(List.of("Unable to generate suggestions at this time"));
        }
    }

    private EditSuggestionService.EditSuggestionRequest createSuggestionRequest(
            Map<String, String> templateScene, 
            Map<String, String> userScene, 
            double overallSimilarity) {
        
        EditSuggestionService.EditSuggestionRequest request = new EditSuggestionService.EditSuggestionRequest();
        request.setTemplateDescriptions(templateScene);
        request.setUserDescriptions(userScene);
        
        // Create mock similarity scores based on overall similarity
        Map<String, Double> scores = new HashMap<>();
        for (String key : templateScene.keySet()) {
            scores.put(key, overallSimilarity + (Math.random() * 0.2 - 0.1)); // Add some variance
        }
        request.setSimilarityScores(scores);
        
        return request;
    }

    private double calculateOverallSimilarity(List<Map<String, String>> userScenes, 
                                            List<Map<String, String>> templateScenes) {
        if (userScenes == null || templateScenes == null || userScenes.isEmpty() || templateScenes.isEmpty()) {
            return 0.75; // Default similarity for demo
        }
        // Simple mock calculation - in real implementation, this would use AI comparison
        return 0.70 + (Math.random() * 0.25); // Random between 70-95%
    }

    private Map<String, Object> createDetailedAnalysis(List<Map<String, String>> userScenes, 
                                                     List<Map<String, String>> templateScenes) {
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("totalScenes", userScenes != null ? userScenes.size() : 1);
        analysis.put("matchingScenes", userScenes != null ? (int)(userScenes.size() * 0.8) : 1);
        analysis.put("improvementAreas", List.of("Camera angle", "Lighting", "Audio quality"));
        return analysis;
    }

    private VideoAnalysisResponse createMockAnalysis(String videoId) {
        VideoAnalysisResponse response = new VideoAnalysisResponse();
        response.videoId = videoId;
        response.overallSimilarity = videoId.equals("video1") ? 0.85 : 0.70;
        response.templateTitle = "Summer Launch Campaign";
        response.suggestions = List.of(
            "Improve lighting in the center area - the template expects brighter, more professional lighting",
            "Adjust camera angle to match template framing - move camera slightly closer to subject",
            "Ensure product is prominently displayed in the right section of the frame",
            "Add company logo to background as shown in template",
            "Speak more clearly and maintain eye contact with camera throughout"
        );
        response.detailedAnalysis = Map.of(
            "totalScenes", 3,
            "matchingScenes", 2,
            "improvementAreas", List.of("Lighting", "Product placement", "Audio clarity")
        );
        return response;
    }

    public static class VideoAnalysisResponse {
        public String videoId;
        public double overallSimilarity;
        public String templateTitle;
        public List<String> suggestions;
        public Map<String, Object> detailedAnalysis;

        // Getters and setters
        public String getVideoId() { return videoId; }
        public void setVideoId(String videoId) { this.videoId = videoId; }
        
        public double getOverallSimilarity() { return overallSimilarity; }
        public void setOverallSimilarity(double overallSimilarity) { this.overallSimilarity = overallSimilarity; }
        
        public String getTemplateTitle() { return templateTitle; }
        public void setTemplateTitle(String templateTitle) { this.templateTitle = templateTitle; }
        
        public List<String> getSuggestions() { return suggestions; }
        public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
        
        public Map<String, Object> getDetailedAnalysis() { return detailedAnalysis; }
        public void setDetailedAnalysis(Map<String, Object> detailedAnalysis) { this.detailedAnalysis = detailedAnalysis; }
    }
}