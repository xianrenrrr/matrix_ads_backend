package com.example.demo.controller.contentmanager;

import com.example.demo.model.AIApprovalThreshold;
import com.example.demo.service.AIApprovalThresholdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ai-approval")
@CrossOrigin(origins = {"http://localhost:4040", "https://matrix-ads-frontend.onrender.com"})
public class AIApprovalThresholdController {
    
    @Autowired
    private AIApprovalThresholdService thresholdService;
    
    @GetMapping("/threshold")
    public ResponseEntity<Map<String, Object>> getThreshold(
            @RequestParam(required = false) String templateId,
            @RequestParam(required = false) String managerId) {
        
        try {
            Optional<AIApprovalThreshold> thresholdOpt;
            
            if (templateId != null && managerId != null) {
                thresholdOpt = thresholdService.getThresholdByTemplateAndManager(templateId, managerId);
            } else if (templateId != null) {
                thresholdOpt = thresholdService.getThresholdByTemplateId(templateId);
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Either templateId or both templateId and managerId are required"
                ));
            }
            
            if (thresholdOpt.isPresent()) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Threshold retrieved successfully",
                    "data", thresholdOpt.get()
                ));
            } else {
                // Create default threshold if none exists
                AIApprovalThreshold defaultThreshold = thresholdService.createDefaultThreshold(templateId, managerId);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Default threshold created and retrieved",
                    "data", defaultThreshold
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error retrieving threshold: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/threshold/{id}")
    public ResponseEntity<Map<String, Object>> getThresholdById(@PathVariable String id) {
        try {
            Optional<AIApprovalThreshold> thresholdOpt = thresholdService.getThresholdById(id);
            
            if (thresholdOpt.isPresent()) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Threshold retrieved successfully",
                    "data", thresholdOpt.get()
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error retrieving threshold: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/thresholds/manager/{managerId}")
    public ResponseEntity<Map<String, Object>> getThresholdsByManager(@PathVariable String managerId) {
        try {
            List<AIApprovalThreshold> thresholds = thresholdService.getThresholdsByManagerId(managerId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Thresholds retrieved successfully",
                "data", thresholds
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error retrieving thresholds: " + e.getMessage()
            ));
        }
    }
    
    @PostMapping("/threshold")
    public ResponseEntity<Map<String, Object>> createThreshold(@RequestBody AIApprovalThreshold threshold) {
        try {
            String id = thresholdService.createThreshold(threshold);
            threshold.setId(id);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Threshold created successfully",
                "data", threshold
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error creating threshold: " + e.getMessage()
            ));
        }
    }
    
    @PutMapping("/threshold")
    public ResponseEntity<Map<String, Object>> updateThreshold(@RequestBody AIApprovalThreshold threshold) {
        try {
            if (threshold.getId() == null || threshold.getId().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Threshold ID is required for updates"
                ));
            }
            
            thresholdService.updateThreshold(threshold);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Threshold updated successfully",
                "data", threshold
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error updating threshold: " + e.getMessage()
            ));
        }
    }
    
    @DeleteMapping("/threshold/{id}")
    public ResponseEntity<Map<String, Object>> deleteThreshold(@PathVariable String id) {
        try {
            thresholdService.deleteThreshold(id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Threshold deleted successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error deleting threshold: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/analytics/{managerId}")
    public ResponseEntity<Map<String, Object>> getAnalytics(@PathVariable String managerId) {
        try {
            Map<String, Object> analytics = thresholdService.getAnalytics(managerId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Analytics retrieved successfully",
                "data", analytics
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error retrieving analytics: " + e.getMessage()
            ));
        }
    }
    
    @PostMapping("/override")
    public ResponseEntity<Map<String, Object>> overrideDecision(@RequestBody Map<String, Object> request) {
        try {
            String sceneSubmissionId = (String) request.get("sceneSubmissionId");
            String managerId = (String) request.get("managerId");
            boolean approved = (Boolean) request.get("approved");
            String reason = (String) request.get("reason");
            
            if (sceneSubmissionId == null || managerId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Scene submission ID and manager ID are required"
                ));
            }
            
            // TODO: Implement override logic in SceneSubmissionService
            // This would update the scene submission with manual override information
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Decision override recorded successfully",
                "data", Map.of(
                    "sceneSubmissionId", sceneSubmissionId,
                    "managerId", managerId,
                    "approved", approved,
                    "reason", reason != null ? reason : ""
                )
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error recording override: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/effective-threshold")
    public ResponseEntity<Map<String, Object>> getEffectiveThreshold(
            @RequestParam String templateId,
            @RequestParam(required = false) String sceneId) {
        
        try {
            if (sceneId != null) {
                double threshold = thresholdService.getEffectiveThresholdForScene(templateId, sceneId);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Effective threshold retrieved successfully",
                    "data", Map.of(
                        "templateId", templateId,
                        "sceneId", sceneId,
                        "threshold", threshold
                    )
                ));
            } else {
                boolean enabled = thresholdService.isAutoApprovalEnabledForTemplate(templateId);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Auto approval status retrieved successfully",
                    "data", Map.of(
                        "templateId", templateId,
                        "autoApprovalEnabled", enabled
                    )
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error retrieving effective threshold: " + e.getMessage()
            ));
        }
    }
}