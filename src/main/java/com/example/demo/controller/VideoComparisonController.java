package com.example.demo.controller;

import com.example.demo.ai.comparison.VideoComparisonService;
import com.example.demo.ai.comparison.VideoComparisonIntegrationService;
import com.example.demo.ai.comparison.ComparisonResult;
import com.example.demo.ai.comparison.dto.ComparisonRequest;
import com.example.demo.ai.comparison.dto.ComparisonResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comparison")
public class VideoComparisonController {

    @Autowired
    private VideoComparisonService videoComparisonService;
    
    @Autowired
    private VideoComparisonIntegrationService integrationService;

    /**
     * Compare a user video against a reference template
     * POST /api/comparison/compare
     */
    @PostMapping("/compare")
    public ResponseEntity<ComparisonResponse> compareVideoToTemplate(
            @RequestBody ComparisonRequest request) {
        
        System.out.printf("Comparison request: template=%s, userVideo=%s%n", 
                         request.getTemplateId(), request.getUserVideoId());
        
        try {
            // Validate request
            if (request.getTemplateId() == null || request.getUserVideoId() == null) {
                return ResponseEntity.badRequest()
                    .body(ComparisonResponse.error("Template ID and User Video ID are required"));
            }
            
            // Get template data using integration service
            List<Map<String, String>> templateScenes = integrationService.getTemplateScenesById(request.getTemplateId());
            if (templateScenes.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ComparisonResponse.error("Template not found or has no processed scenes"));
            }
            
            // Get user video data using integration service
            List<Map<String, String>> userScenes = integrationService.getUserVideoScenesById(request.getUserVideoId());
            if (userScenes.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ComparisonResponse.error("User video not found or has no processed scenes"));
            }
            
            // Perform comparison
            ComparisonResult result = videoComparisonService.compareVideoToTemplate(
                templateScenes, userScenes
            );
            
            // Generate detailed report
            String report = videoComparisonService.generateComparisonReport(result);
            
            return ResponseEntity.ok(ComparisonResponse.success(result, report));
            
        } catch (Exception e) {
            System.err.printf("Error during video comparison: %s%n", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(ComparisonResponse.error("Internal server error during comparison: " + e.getMessage()));
        }
    }

    /**
     * Compare two templates directly
     * POST /api/comparison/templates
     */
    @PostMapping("/templates")
    public ResponseEntity<ComparisonResponse> compareTemplates(
            @RequestParam String template1Id,
            @RequestParam String template2Id) {
        
        System.out.printf("Template comparison request: %s vs %s%n", template1Id, template2Id);
        
        try {
            // Get both templates using integration service
            List<Map<String, String>> template1Scenes = integrationService.getTemplateScenesById(template1Id);
            List<Map<String, String>> template2Scenes = integrationService.getTemplateScenesById(template2Id);
            
            if (template1Scenes.isEmpty() || template2Scenes.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ComparisonResponse.error("One or both templates not found"));
            }
            
            // Perform comparison
            ComparisonResult result = videoComparisonService.compareVideoToTemplate(
                template1Scenes, template2Scenes
            );
            
            // Generate report
            String report = videoComparisonService.generateComparisonReport(result);
            
            return ResponseEntity.ok(ComparisonResponse.success(result, report));
            
        } catch (Exception e) {
            System.err.printf("Error during template comparison: %s%n", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(ComparisonResponse.error("Template comparison failed: " + e.getMessage()));
        }
    }

    /**
     * Get similarity score only (lightweight endpoint)
     * GET /api/comparison/score?templateId=...&userVideoId=...
     */
    @GetMapping("/score")
    public ResponseEntity<Map<String, Object>> getQuickSimilarityScore(
            @RequestParam String templateId,
            @RequestParam String userVideoId) {
        
        try {
            List<Map<String, String>> templateScenes = integrationService.getTemplateScenesById(templateId);
            List<Map<String, String>> userScenes = integrationService.getUserVideoScenesById(userVideoId);
            
            if (templateScenes.isEmpty() || userScenes.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Template or user video not found");
                return ResponseEntity.badRequest().body(error);
            }
            
            ComparisonResult result = videoComparisonService.compareVideoToTemplate(
                templateScenes, userScenes
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("overallScore", result.getOverallScore());
            response.put("scorePercentage", Math.round(result.getOverallScore() * 100));
            response.put("scenesCompared", result.getSceneComparisons().size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

}