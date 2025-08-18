package com.example.demo.ai.core;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for AI model switching and provider settings
 * Centralized configuration management for all AI providers
 */
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AIConfiguration {
    
    /**
     * Global AI settings
     */
    private boolean enabled = true;
    private String defaultLanguage = "zh-CN";
    private int globalTimeoutMs = 30000;
    
    /**
     * Provider-specific configurations
     */
    private Map<String, ProviderConfig> providers = new HashMap<>();
    
    /**
     * Model switching preferences
     */
    private ModelSwitching switching = new ModelSwitching();
    
    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public String getDefaultLanguage() { return defaultLanguage; }
    public void setDefaultLanguage(String defaultLanguage) { this.defaultLanguage = defaultLanguage; }
    
    public int getGlobalTimeoutMs() { return globalTimeoutMs; }
    public void setGlobalTimeoutMs(int globalTimeoutMs) { this.globalTimeoutMs = globalTimeoutMs; }
    
    public Map<String, ProviderConfig> getProviders() { return providers; }
    public void setProviders(Map<String, ProviderConfig> providers) { this.providers = providers; }
    
    public ModelSwitching getSwitching() { return switching; }
    public void setSwitching(ModelSwitching switching) { this.switching = switching; }
    
    /**
     * Configuration for individual providers
     */
    public static class ProviderConfig {
        private boolean enabled = true;
        private int priority = 50;
        private String endpoint;
        private String apiKey;
        private String model;
        private int timeoutMs = 30000;
        private Map<String, Object> parameters = new HashMap<>();
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    }
    
    /**
     * Model switching configuration
     */
    public static class ModelSwitching {
        private boolean enableFallback = true;
        private int maxRetries = 3;
        private boolean enableHealthCheck = true;
        private int healthCheckIntervalMs = 60000;
        private Map<String, String> preferredProviders = new HashMap<>();
        
        // Getters and setters
        public boolean isEnableFallback() { return enableFallback; }
        public void setEnableFallback(boolean enableFallback) { this.enableFallback = enableFallback; }
        
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        
        public boolean isEnableHealthCheck() { return enableHealthCheck; }
        public void setEnableHealthCheck(boolean enableHealthCheck) { this.enableHealthCheck = enableHealthCheck; }
        
        public int getHealthCheckIntervalMs() { return healthCheckIntervalMs; }
        public void setHealthCheckIntervalMs(int healthCheckIntervalMs) { this.healthCheckIntervalMs = healthCheckIntervalMs; }
        
        public Map<String, String> getPreferredProviders() { return preferredProviders; }
        public void setPreferredProviders(Map<String, String> preferredProviders) { this.preferredProviders = preferredProviders; }
    }
}