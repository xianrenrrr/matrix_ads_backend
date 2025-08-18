package com.example.demo.ai.services;

import com.example.demo.ai.core.AIModelProvider;
import com.example.demo.ai.core.AIModelType;
import com.example.demo.ai.core.AIResponse;
import com.example.demo.ai.providers.vision.VisionProvider;
import com.example.demo.ai.providers.llm.LLMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Main service coordinator for AI operations
 * Handles provider selection, fallback logic, and model switching
 */
@Service
public class AIOrchestrator {
    
    private static final Logger log = LoggerFactory.getLogger(AIOrchestrator.class);
    
    private final Map<AIModelType, List<AIModelProvider>> providers = new HashMap<>();
    
    @Autowired
    public AIOrchestrator(List<VisionProvider> visionProviders,
                         List<LLMProvider> llmProviders) {
        // Register all providers by type
        providers.put(AIModelType.VISION, new ArrayList<>(visionProviders));
        providers.put(AIModelType.SEGMENTATION, new ArrayList<>(visionProviders)); // Vision providers handle segmentation
        providers.put(AIModelType.LLM, new ArrayList<>(llmProviders));
        
        // Sort providers by priority (highest first)
        providers.values().forEach(providerList -> 
            providerList.sort((p1, p2) -> Integer.compare(p2.getPriority(), p1.getPriority())));
        
        log.info("AI Orchestrator initialized with {} provider types", providers.size());
        providers.forEach((type, providerList) -> 
            log.info("  {}: {} providers", type, providerList.size()));
    }
    
    /**
     * Get the best available provider for a specific model type and operation
     */
    public <T extends AIModelProvider> Optional<T> getProvider(AIModelType modelType, String operation) {
        List<AIModelProvider> availableProviders = providers.get(modelType);
        if (availableProviders == null || availableProviders.isEmpty()) {
            log.warn("No providers available for model type: {}", modelType);
            return Optional.empty();
        }
        
        // Find the highest priority provider that's available and supports the operation
        for (AIModelProvider provider : availableProviders) {
            if (provider.isAvailable() && provider.supportsOperation(operation)) {
                log.debug("Selected provider: {} for operation: {}", provider.getProviderName(), operation);
                return Optional.of((T) provider);
            }
        }
        
        log.warn("No available providers found for model type: {} and operation: {}", modelType, operation);
        return Optional.empty();
    }
    
    /**
     * Get a specific provider by name
     */
    public <T extends AIModelProvider> Optional<T> getProviderByName(String providerName) {
        return providers.values().stream()
            .flatMap(List::stream)
            .filter(provider -> provider.getProviderName().equals(providerName))
            .map(provider -> (T) provider)
            .findFirst();
    }
    
    /**
     * Get all providers of a specific type
     */
    public <T extends AIModelProvider> List<T> getProviders(AIModelType modelType) {
        return providers.getOrDefault(modelType, Collections.emptyList())
            .stream()
            .map(provider -> (T) provider)
            .collect(Collectors.toList());
    }
    
    /**
     * Execute an operation with automatic fallback
     */
    public <T> AIResponse<T> executeWithFallback(AIModelType modelType, String operation, 
                                                 ProviderOperation<T> operationFunc) {
        List<AIModelProvider> availableProviders = providers.get(modelType);
        if (availableProviders == null || availableProviders.isEmpty()) {
            return AIResponse.error("No providers available for model type: " + modelType);
        }
        
        Exception lastException = null;
        
        // Try each provider in priority order
        for (AIModelProvider provider : availableProviders) {
            if (!provider.isAvailable() || !provider.supportsOperation(operation)) {
                continue;
            }
            
            try {
                long startTime = System.currentTimeMillis();
                AIResponse<T> result = operationFunc.execute(provider);
                
                if (result.isSuccess()) {
                    result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                    result.setModelUsed(provider.getProviderName());
                    result.setModelType(modelType);
                    log.debug("Operation {} succeeded with provider: {}", operation, provider.getProviderName());
                    return result;
                }
                
                log.warn("Operation {} failed with provider: {} - Error: {}", 
                    operation, provider.getProviderName(), result.getError());
                
            } catch (Exception e) {
                lastException = e;
                log.warn("Operation {} threw exception with provider: {} - {}", 
                    operation, provider.getProviderName(), e.getMessage());
            }
        }
        
        String errorMsg = "All providers failed for operation: " + operation;
        if (lastException != null) {
            errorMsg += " - Last error: " + lastException.getMessage();
        }
        
        log.error(errorMsg);
        return AIResponse.error(errorMsg);
    }
    
    /**
     * Get system health status
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> status = new HashMap<>();
        
        providers.forEach((type, providerList) -> {
            Map<String, Object> typeStatus = new HashMap<>();
            typeStatus.put("total", providerList.size());
            typeStatus.put("available", providerList.stream().mapToInt(p -> p.isAvailable() ? 1 : 0).sum());
            
            List<Map<String, Object>> providerDetails = providerList.stream()
                .map(provider -> {
                    Map<String, Object> details = new HashMap<>();
                    details.put("name", provider.getProviderName());
                    details.put("available", provider.isAvailable());
                    details.put("priority", provider.getPriority());
                    return details;
                })
                .collect(Collectors.toList());
            
            typeStatus.put("providers", providerDetails);
            status.put(type.toString().toLowerCase(), typeStatus);
        });
        
        return status;
    }
    
    /**
     * Functional interface for provider operations
     */
    @FunctionalInterface
    public interface ProviderOperation<T> {
        AIResponse<T> execute(AIModelProvider provider) throws Exception;
    }
}