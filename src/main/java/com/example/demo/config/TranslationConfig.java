package com.example.demo.config;

import com.example.demo.ai.translate.TranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TranslationConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(TranslationConfig.class);
    
    @Bean
    @ConditionalOnProperty(name = "ai.labels.localization.enabled", havingValue = "true", matchIfMissing = true)
    public TranslationService translationService() {
        logger.info("Initializing TranslationService with localization enabled");
        return new TranslationService();
    }
}