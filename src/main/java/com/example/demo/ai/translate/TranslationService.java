package com.example.demo.ai.translate;

import com.google.cloud.translate.v3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class TranslationService {
    private static final Logger logger = LoggerFactory.getLogger(TranslationService.class);
    
    private TranslationServiceClient translationClient;
    private final Map<String, String> translationCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;
    
    @Value("${ai.labels.localization.enabled:true}")
    private boolean localizationEnabled;
    
    @Value("${gcp.project.id:}")
    private String projectId;
    
    public TranslationService() {
        if (localizationEnabled) {
            try {
                this.translationClient = TranslationServiceClient.create();
                logger.info("Translation service initialized successfully");
            } catch (Exception e) {
                logger.warn("Failed to initialize Translation API client: {}", e.getMessage());
                this.translationClient = null;
            }
        }
    }
    
    /**
     * Translate a single label with caching
     * @param text English label
     * @param targetLocale Target locale (e.g., "zh-CN")
     * @return Translated label or original if translation fails
     */
    public String translateLabel(String text, String targetLocale) {
        if (!isEnabled() || text == null || text.isEmpty()) {
            return text;
        }
        
        String cacheKey = buildCacheKey(text, targetLocale);
        String cached = translationCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        try {
            Map<String, String> result = translateLabelsInternal(Arrays.asList(text), targetLocale);
            String translated = result.getOrDefault(text, text);
            cacheTranslation(text, translated, targetLocale);
            return translated;
        } catch (Exception e) {
            logger.warn("Failed to translate '{}': {}", text, e.getMessage());
            return text;
        }
    }
    
    /**
     * Batch translate labels (preferred method for efficiency)
     * @param texts Collection of English labels
     * @param targetLocale Target locale (e.g., "zh-CN")
     * @return Map of original label to translated label
     */
    public Map<String, String> translateLabels(Collection<String> texts, String targetLocale) {
        Map<String, String> results = new HashMap<>();
        
        if (!isEnabled() || texts == null || texts.isEmpty()) {
            // Return original labels as fallback
            for (String text : texts) {
                results.put(text, text);
            }
            return results;
        }
        
        List<String> textsToTranslate = new ArrayList<>();
        
        // Check cache first
        for (String text : texts) {
            String cacheKey = buildCacheKey(text, targetLocale);
            String cached = translationCache.get(cacheKey);
            if (cached != null) {
                results.put(text, cached);
            } else {
                textsToTranslate.add(text);
            }
        }
        
        // Translate uncached labels
        if (!textsToTranslate.isEmpty()) {
            try {
                Map<String, String> translations = translateLabelsInternal(textsToTranslate, targetLocale);
                
                // Cache and add to results
                for (Map.Entry<String, String> entry : translations.entrySet()) {
                    String original = entry.getKey();
                    String translated = entry.getValue();
                    cacheTranslation(original, translated, targetLocale);
                    results.put(original, translated);
                }
            } catch (Exception e) {
                logger.warn("Translation failed, using English fallback: {}", e.getMessage());
                // Use English as fallback
                for (String text : textsToTranslate) {
                    results.put(text, text);
                }
            }
        }
        
        return results;
    }
    
    /**
     * Perform actual translation using Google Cloud Translate v3 API
     */
    private Map<String, String> translateLabelsInternal(List<String> texts, String targetLocale) throws Exception {
        Map<String, String> translations = new HashMap<>();
        
        if (translationClient == null || projectId == null || projectId.isEmpty()) {
            logger.warn("Translation client or project ID not available, using fallback");
            for (String text : texts) {
                translations.put(text, text);
            }
            return translations;
        }
        
        // Build the request
        LocationName parent = LocationName.of(projectId, "global");
        TranslateTextRequest request = TranslateTextRequest.newBuilder()
            .setParent(parent.toString())
            .setMimeType("text/plain")
            .setTargetLanguageCode(convertLocaleToLanguageCode(targetLocale))
            .addAllContents(texts)
            .build();
        
        // Perform translation
        TranslateTextResponse response = translationClient.translateText(request);
        
        // Map results back to original texts
        List<Translation> translationsList = response.getTranslationsList();
        for (int i = 0; i < texts.size() && i < translationsList.size(); i++) {
            String original = texts.get(i);
            String translated = translationsList.get(i).getTranslatedText();
            translations.put(original, translated);
        }
        
        logger.debug("Translated {} labels to {}", texts.size(), targetLocale);
        return translations;
    }
    
    /**
     * Convert locale format to language code for Translate API
     */
    private String convertLocaleToLanguageCode(String locale) {
        if (locale == null || locale.isEmpty()) {
            return "zh-CN"; // Default to Simplified Chinese
        }
        
        // Handle common locale formats
        switch (locale.toLowerCase()) {
            case "zh-cn":
            case "zh_cn":
                return "zh-CN";
            case "zh-tw":
            case "zh_tw":
                return "zh-TW";
            default:
                // Extract language code from locale (e.g., "en-US" -> "en")
                String[] parts = locale.split("[-_]");
                return parts[0].toLowerCase();
        }
    }
    
    private String buildCacheKey(String text, String locale) {
        return locale + "|" + text;
    }
    
    private void cacheTranslation(String original, String translated, String locale) {
        // Simple cache management: clear if too large
        if (translationCache.size() >= MAX_CACHE_SIZE) {
            translationCache.clear();
            logger.debug("Translation cache cleared (reached max size)");
        }
        
        String cacheKey = buildCacheKey(original, locale);
        translationCache.put(cacheKey, translated);
    }
    
    private boolean isEnabled() {
        return localizationEnabled && translationClient != null;
    }
    
    /**
     * Clean up resources
     */
    public void close() {
        if (translationClient != null) {
            translationClient.close();
        }
    }
}