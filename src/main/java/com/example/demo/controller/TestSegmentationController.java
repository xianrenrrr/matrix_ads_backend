package com.example.demo.controller;

import com.example.demo.ai.seg.SegmentationService;
import com.example.demo.ai.seg.dto.OverlayShape;
import com.example.demo.api.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test controller for PaddleDet segmentation service
 * FOR TESTING ONLY - Remove in production
 */
@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = "*")
public class TestSegmentationController {
    
    @Autowired
    @Qualifier("paddleDetSegService")
    private SegmentationService segmentationService;
    
    /**
     * Test endpoint for segmentation service
     * POST /api/test/segment
     * Body: { "keyframeUrl": "https://example.com/image.jpg" }
     */
    @PostMapping("/segment")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testSegmentation(
            @RequestBody Map<String, String> request) {
        try {
            String keyframeUrl = request.get("keyframeUrl");
            if (keyframeUrl == null || keyframeUrl.trim().isEmpty()) {
                // Use a default test image if none provided
                keyframeUrl = "https://via.placeholder.com/640x480.png?text=Test+Image";
            }
            
            // Call the segmentation service
            List<OverlayShape> shapes = segmentationService.detect(keyframeUrl);
            
            // Prepare response
            Map<String, Object> result = new HashMap<>();
            result.put("keyframeUrl", keyframeUrl);
            result.put("shapesCount", shapes.size());
            result.put("shapes", shapes);
            result.put("serviceType", "PaddleDet (Stub Mode)");
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(ApiResponse.ok(
                "Segmentation test completed. Found " + shapes.size() + " shapes.", 
                result
            ));
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            error.put("type", e.getClass().getSimpleName());
            
            return ResponseEntity.ok(ApiResponse.fail(
                "Segmentation test failed: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Simple GET endpoint to check if test endpoints are working
     */
    @GetMapping("/segment/status")
    public ResponseEntity<ApiResponse<Map<String, String>>> getSegmentationStatus() {
        Map<String, String> status = new HashMap<>();
        status.put("service", "PaddleDet Segmentation");
        status.put("status", "ready");
        status.put("mode", "stub");
        status.put("endpoint", "/api/test/segment");
        
        return ResponseEntity.ok(ApiResponse.ok("Test endpoint is ready", status));
    }
}