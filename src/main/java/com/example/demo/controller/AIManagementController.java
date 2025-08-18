package com.example.demo.controller;

import com.example.demo.ai.core.AIConfiguration;
import com.example.demo.ai.services.AIOrchestrator;
import com.example.demo.api.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Management controller for AI system monitoring and configuration
 * Provides endpoints for health checks, configuration updates, and system status
 */
@RestController
@RequestMapping("/api/ai-management")
@CrossOrigin(origins = {"http://localhost:4040", "https://matrix-ads-frontend.onrender.com"})
public class AIManagementController {
    
    @Autowired
    private AIOrchestrator aiOrchestrator;
    
    @Autowired
    private AIConfiguration aiConfiguration;
    
    /**
     * Get comprehensive AI system health status
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHealthStatus() {
        try {
            Map<String, Object> healthStatus = aiOrchestrator.getHealthStatus();
            
            Map<String, Object> systemStatus = new HashMap<>();
            systemStatus.put("ai_enabled", aiConfiguration.isEnabled());
            systemStatus.put("default_language", aiConfiguration.getDefaultLanguage());
            systemStatus.put("global_timeout_ms", aiConfiguration.getGlobalTimeoutMs());
            systemStatus.put("providers", healthStatus);
            
            // Add overall system health
            boolean overallHealthy = healthStatus.values().stream()
                .allMatch(providerStatus -> {
                    if (providerStatus instanceof Map) {
                        Map<?, ?> status = (Map<?, ?>) providerStatus;
                        Object available = status.get("available");
                        return available instanceof Number && ((Number) available).intValue() > 0;
                    }
                    return false;
                });
            
            systemStatus.put("overall_healthy", overallHealthy);
            systemStatus.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(ApiResponse.ok("AI system health retrieved", systemStatus));
            
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.fail("Failed to get AI health status: " + e.getMessage()));
        }
    }
    
    /**
     * Get current AI configuration
     */
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<AIConfiguration>> getConfiguration() {
        try {
            return ResponseEntity.ok(ApiResponse.ok("AI configuration retrieved", aiConfiguration));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.fail("Failed to get AI configuration: " + e.getMessage()));
        }
    }
    
    /**
     * Update AI provider configuration
     */
    @PutMapping("/config/provider/{providerName}")
    public ResponseEntity<ApiResponse<String>> updateProviderConfig(
            @PathVariable String providerName,
            @RequestBody Map<String, Object> config) {
        try {
            // Get the provider and update its configuration
            var provider = aiOrchestrator.getProviderByName(providerName);
            if (provider.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.fail("Provider not found: " + providerName));
            }
            
            provider.get().initialize(config);
            
            // Update configuration object
            AIConfiguration.ProviderConfig providerConfig = aiConfiguration.getProviders()
                .computeIfAbsent(providerName, k -> new AIConfiguration.ProviderConfig());
            
            if (config.containsKey("enabled")) {
                providerConfig.setEnabled((Boolean) config.get("enabled"));
            }
            if (config.containsKey("priority")) {
                providerConfig.setPriority((Integer) config.get("priority"));
            }
            providerConfig.getParameters().putAll(config);
            
            return ResponseEntity.ok(ApiResponse.ok("Provider configuration updated", 
                "Provider " + providerName + " configuration updated successfully"));
            
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.fail("Failed to update provider configuration: " + e.getMessage()));
        }
    }
    
    /**
     * Test a specific AI provider
     */
    @PostMapping("/test/{providerName}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testProvider(
            @PathVariable String providerName,
            @RequestBody Map<String, Object> testRequest) {
        try {
            var provider = aiOrchestrator.getProviderByName(providerName);
            if (provider.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.fail("Provider not found: " + providerName));
            }
            
            Map<String, Object> testResult = new HashMap<>();
            testResult.put("provider_name", provider.get().getProviderName());
            testResult.put("model_type", provider.get().getModelType());
            testResult.put("available", provider.get().isAvailable());
            testResult.put("priority", provider.get().getPriority());
            testResult.put("configuration", provider.get().getConfiguration());
            
            // Test specific operations based on provider type
            String testOperation = (String) testRequest.getOrDefault("operation", "health_check");
            testResult.put("supports_operation", provider.get().supportsOperation(testOperation));
            testResult.put("test_timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(ApiResponse.ok("Provider test completed", testResult));
            
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.fail("Provider test failed: " + e.getMessage()));
        }
    }
    
    /**
     * Get AI system metrics and statistics
     */
    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMetrics() {
        try {
            Map<String, Object> metrics = new HashMap<>();
            
            // Get provider counts by type
            var healthStatus = aiOrchestrator.getHealthStatus();
            int totalProviders = 0;
            int availableProviders = 0;
            
            for (Object providerStatus : healthStatus.values()) {
                if (providerStatus instanceof Map) {
                    Map<?, ?> status = (Map<?, ?>) providerStatus;
                    Object total = status.get("total");
                    Object available = status.get("available");
                    
                    if (total instanceof Number) {
                        totalProviders += ((Number) total).intValue();
                    }
                    if (available instanceof Number) {
                        availableProviders += ((Number) available).intValue();
                    }
                }
            }
            
            metrics.put("total_providers", totalProviders);
            metrics.put("available_providers", availableProviders);
            metrics.put("availability_percentage", totalProviders > 0 ? 
                (double) availableProviders / totalProviders * 100 : 0);
            metrics.put("default_language", aiConfiguration.getDefaultLanguage());
            metrics.put("fallback_enabled", aiConfiguration.getSwitching().isEnableFallback());
            metrics.put("health_check_enabled", aiConfiguration.getSwitching().isEnableHealthCheck());
            metrics.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(ApiResponse.ok("AI metrics retrieved", metrics));
            
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.fail("Failed to get AI metrics: " + e.getMessage()));
        }
    }
}