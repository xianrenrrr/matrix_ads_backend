package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = {
    "classpath:application-local.properties"
}, ignoreResourceNotFound = true)
@ConfigurationProperties(prefix = "openai")
public class DeepSeekConfig {
    
    private Api api = new Api();
    
    public Api getApi() {
        return api;
    }
    
    public void setApi(Api api) {
        this.api = api;
    }
    
    public static class Api {
        private String key;
        private String keyLocal;
        
        public String getKey() {
            return key;
        }
        
        public void setKey(String key) {
            this.key = key;
        }
        
        public String getKeyLocal() {
            return keyLocal;
        }
        
        public void setKeyLocal(String keyLocal) {
            this.keyLocal = keyLocal;
        }
        
        /**
         * Get the effective API key, prioritizing environment variable over local config
         */
        public String getEffectiveKey() {
            // Priority: Environment variable > Local config file > null
            if (key != null && !key.trim().isEmpty()) {
                return key;
            }
            if (keyLocal != null && !keyLocal.trim().isEmpty()) {
                return keyLocal;
            }
            return null;
        }
    }
}