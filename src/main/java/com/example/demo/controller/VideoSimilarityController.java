package com.example.demo.controller;

import com.example.demo.ai.EditSuggestionService;
import com.example.demo.ai.comparison.VideoComparisonIntegrationService;
import com.example.demo.ai.comparison.VideoComparisonService;
import com.example.demo.dao.VideoDao;
import com.example.demo.dao.TemplateDao;
import com.example.demo.model.Video;
import com.example.demo.model.ManualTemplate;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
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
    
    @Autowired
    private Firestore db;

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

            // Get submitted video from submittedVideos collection
            System.out.println("Retrieving pre-computed analysis for video ID: " + videoId);
            
            // Query submittedVideos collection directly since that's where the analysis is stored
            DocumentReference docRef = db.collection("submittedVideos").document(videoId);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot document = future.get();
            
            if (!document.exists()) {
                System.out.println("Submitted video not found, returning 404");
                return ResponseEntity.notFound().build();
            }
            
            // Extract pre-computed data from feedback object
            Map<String, Object> feedback = (Map<String, Object>) document.get("feedback");
            String templateId = document.getString("templateId");
            
            double overallSimilarity = 0.0;
            List<String> suggestions = List.of("No suggestions available");
            String templateTitle = "Unknown Template";
            
            if (feedback != null) {
                Object similarityObj = feedback.get("similarityScore");
                if (similarityObj instanceof Number) {
                    overallSimilarity = ((Number) similarityObj).doubleValue();
                }
                
                Object suggestionsObj = feedback.get("suggestions");
                if (suggestionsObj instanceof List) {
                    suggestions = (List<String>) suggestionsObj;
                }
            }
            
            // Get template title
            if (templateId != null && templateDao != null) {
                ManualTemplate template = templateDao.getTemplate(templateId);
                if (template != null) {
                    templateTitle = template.getTemplateTitle();
                }
            }
            
            System.out.println("Retrieved similarity: " + overallSimilarity + ", suggestions: " + suggestions.size());

            VideoAnalysisResponse response = new VideoAnalysisResponse();
            response.videoId = videoId;
            response.overallSimilarity = overallSimilarity;
            response.templateTitle = templateTitle;
            response.suggestions = suggestions;
            response.detailedAnalysis = createDetailedAnalysis(null, null); // Mock detailed analysis

            return ResponseEntity.ok(response);

        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.ok(createMockAnalysis(videoId));
        } catch (Exception e) {
            return ResponseEntity.ok(createMockAnalysis(videoId));
        }
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