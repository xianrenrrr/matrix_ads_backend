package com.example.demo.ai.core;

import java.util.Map;

/**
 * Main abstraction interface for all AI model providers
 * Provides a unified interface for different AI capabilities
 */
public interface AIModelProvider {
    
    /**
     * Get the type of AI model this provider handles
     */
    AIModelType getModelType();
    
    /**
     * Get the name/identifier of this provider
     */
    String getProviderName();
    
    /**
     * Check if this provider is currently available/healthy
     */
    boolean isAvailable();
    
    /**
     * Get configuration information for this provider
     */
    Map<String, Object> getConfiguration();
    
    /**
     * Initialize the provider with given configuration
     */
    void initialize(Map<String, Object> config);
    
    /**
     * Get the priority of this provider (higher = preferred)
     * Used for automatic fallback selection
     */
    int getPriority();
    
    /**
     * Check if this provider supports the given operation
     */
    boolean supportsOperation(String operation);
}