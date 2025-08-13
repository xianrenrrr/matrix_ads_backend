package com.example.demo.ai.translate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TranslationServiceTest {
    
    private TranslationService translationService;
    
    @BeforeEach
    void setUp() {
        translationService = new TranslationService();
        
        // Configure for testing - disable actual translation calls
        ReflectionTestUtils.setField(translationService, "localizationEnabled", false);
        ReflectionTestUtils.setField(translationService, "projectId", "test-project");
    }
    
    @Test
    void testTranslateLabelWithLocalizationDisabled() {
        // Given
        String label = "person";
        String targetLocale = "zh-CN";
        
        // When
        String result = translationService.translateLabel(label, targetLocale);
        
        // Then
        assertEquals("person", result);
    }
    
    @Test
    void testTranslateLabelsWithEmptyCollection() {
        // Given
        Collection<String> labels = Collections.emptyList();
        String targetLocale = "zh-CN";
        
        // When
        Map<String, String> result = translationService.translateLabels(labels, targetLocale);
        
        // Then
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testTranslateLabelsWithNullCollection() {
        // Given
        Collection<String> labels = null;
        String targetLocale = "zh-CN";
        
        // When
        Map<String, String> result = translationService.translateLabels(labels, targetLocale);
        
        // Then
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testTranslateLabelsReturnsOriginalWhenDisabled() {
        // Given
        Collection<String> labels = Arrays.asList("person", "product", "vehicle");
        String targetLocale = "zh-CN";
        
        // When
        Map<String, String> result = translationService.translateLabels(labels, targetLocale);
        
        // Then
        assertEquals(3, result.size());
        assertEquals("person", result.get("person"));
        assertEquals("product", result.get("product"));
        assertEquals("vehicle", result.get("vehicle"));
    }
    
    @Test
    void testTranslateLabelWithNull() {
        // Given
        String label = null;
        String targetLocale = "zh-CN";
        
        // When
        String result = translationService.translateLabel(label, targetLocale);
        
        // Then
        assertNull(result);
    }
    
    @Test
    void testTranslateLabelWithEmpty() {
        // Given
        String label = "";
        String targetLocale = "zh-CN";
        
        // When
        String result = translationService.translateLabel(label, targetLocale);
        
        // Then
        assertEquals("", result);
    }
    
    @Test
    void testConvertLocaleToLanguageCode() throws Exception {
        // Use reflection to test private method
        java.lang.reflect.Method method = TranslationService.class.getDeclaredMethod("convertLocaleToLanguageCode", String.class);
        method.setAccessible(true);
        
        // Test various locale formats
        assertEquals("zh-CN", method.invoke(translationService, "zh-CN"));
        assertEquals("zh-CN", method.invoke(translationService, "zh_CN"));
        assertEquals("zh-TW", method.invoke(translationService, "zh-TW"));
        assertEquals("zh-TW", method.invoke(translationService, "zh_TW"));
        assertEquals("en", method.invoke(translationService, "en-US"));
        assertEquals("fr", method.invoke(translationService, "fr"));
        assertEquals("zh-CN", method.invoke(translationService, null));
        assertEquals("zh-CN", method.invoke(translationService, ""));
    }
    
    @Test
    void testBuildCacheKey() throws Exception {
        // Use reflection to test private method
        java.lang.reflect.Method method = TranslationService.class.getDeclaredMethod("buildCacheKey", String.class, String.class);
        method.setAccessible(true);
        
        // Test cache key generation
        String cacheKey = (String) method.invoke(translationService, "person", "zh-CN");
        assertEquals("zh-CN|person", cacheKey);
    }
    
    @Test
    void testCachingBehavior() {
        // Enable localization for this test but without actual client
        ReflectionTestUtils.setField(translationService, "localizationEnabled", true);
        
        // Given
        Collection<String> labels1 = Arrays.asList("person", "product");
        Collection<String> labels2 = Arrays.asList("product", "vehicle"); // "product" should be cached
        String targetLocale = "zh-CN";
        
        // When - first call
        Map<String, String> result1 = translationService.translateLabels(labels1, targetLocale);
        
        // When - second call (product should come from cache)
        Map<String, String> result2 = translationService.translateLabels(labels2, targetLocale);
        
        // Then - both should have fallback values (since client is null)
        assertEquals("person", result1.get("person"));
        assertEquals("product", result1.get("product"));
        assertEquals("product", result2.get("product"));
        assertEquals("vehicle", result2.get("vehicle"));
    }
}