package com.example.demo.controller.contentmanager;

import com.example.demo.service.TemplateSubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/template-subscription")
@CrossOrigin(origins = {"http://localhost:4040", "https://matrix-ads-frontend.onrender.com"})
public class TemplateSubscriptionController {
    
    @Autowired
    private TemplateSubscriptionService templateSubscriptionService;
    
    @PostMapping("/batch-subscribe")
    public ResponseEntity<Map<String, Object>> batchSubscribeToTemplate(@RequestBody Map<String, Object> request) {
        try {
            String templateId = (String) request.get("templateId");
            @SuppressWarnings("unchecked")
            List<String> groupIds = (List<String>) request.get("groupIds");
            
            if (templateId == null || templateId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "HARDCODED_Template ID is required" // TODO: Internationalize this message
                ));
            }
            
            if (groupIds == null || groupIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "HARDCODED_At least one group ID is required" // TODO: Internationalize this message
                ));
            }
            
            // Use shared service for batch subscription
            TemplateSubscriptionService.SubscriptionResult result = 
                templateSubscriptionService.batchSubscribeToTemplate(templateId, groupIds);
            
            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("HARDCODED_Template subscribed to %d users across %d groups", // TODO: Internationalize this message 
                result.getTotalUsersAffected(), result.getProcessedGroups().size()));
            response.put("templateId", templateId);
            response.put("totalUsersSubscribed", result.getTotalUsersAffected());
            response.put("processedGroups", result.getProcessedGroups());
            response.put("groupMemberCounts", result.getGroupMemberCounts());
            
            if (result.hasFailures()) {
                response.put("warnings", result.getFailedGroups());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "HARDCODED_Error processing batch subscription: " + e.getMessage() // TODO: Internationalize this message
            ));
        }
    }
    
    @PostMapping("/unsubscribe-batch")
    public ResponseEntity<Map<String, Object>> batchUnsubscribeFromTemplate(@RequestBody Map<String, Object> request) {
        try {
            String templateId = (String) request.get("templateId");
            @SuppressWarnings("unchecked")
            List<String> groupIds = (List<String>) request.get("groupIds");
            
            if (templateId == null || templateId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "HARDCODED_Template ID is required" // TODO: Internationalize this message
                ));
            }
            
            if (groupIds == null || groupIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "HARDCODED_At least one group ID is required" // TODO: Internationalize this message
                ));
            }
            
            // Use shared service for batch unsubscription
            TemplateSubscriptionService.SubscriptionResult result = 
                templateSubscriptionService.batchUnsubscribeFromTemplate(templateId, groupIds);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("HARDCODED_Template unsubscribed from %d users across %d groups", // TODO: Internationalize this message 
                result.getTotalUsersAffected(), result.getProcessedGroups().size()));
            response.put("templateId", templateId);
            response.put("totalUsersUnsubscribed", result.getTotalUsersAffected());
            response.put("processedGroups", result.getProcessedGroups());
            
            if (result.hasFailures()) {
                response.put("warnings", result.getFailedGroups());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error processing batch unsubscription: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/template/{templateId}/subscribers")
    public ResponseEntity<Map<String, Object>> getTemplateSubscribers(@PathVariable String templateId) {
        try {
            // This would require a reverse lookup - getting all users who have this template subscribed
            // For now, return a placeholder response
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Template subscribers endpoint - implementation needed",
                "templateId", templateId
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error getting template subscribers: " + e.getMessage()
            ));
        }
    }
}