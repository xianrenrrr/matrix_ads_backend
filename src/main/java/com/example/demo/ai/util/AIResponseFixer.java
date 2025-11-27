package com.example.demo.ai.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for fixing common AI response formatting issues.
 * 
 * AI models (especially Qwen) sometimes return malformed JSON:
 * - Unquoted strings in arrays
 * - Missing quotes around Chinese text
 * - Extra text before/after JSON
 * - Markdown code blocks around JSON
 * 
 * This class provides methods to clean and fix these issues.
 */
public class AIResponseFixer {
    
    private static final Logger log = LoggerFactory.getLogger(AIResponseFixer.class);
    
    /**
     * Clean and fix AI response to extract valid JSON.
     * Applies all fixes in sequence.
     * 
     * @param response Raw AI response string
     * @return Cleaned JSON string, or null if no JSON found
     */
    public static String cleanAndFixJson(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }
        
        String cleaned = response;
        
        // Step 1: Remove markdown code blocks
        cleaned = removeMarkdownCodeBlocks(cleaned);
        
        // Step 2: Extract JSON from surrounding text
        cleaned = extractJsonObject(cleaned);
        
        if (cleaned == null) {
            log.warn("[AIResponseFixer] No JSON object found in response");
            return null;
        }
        
        // Step 3: Fix unquoted strings in arrays
        cleaned = fixUnquotedArrayStrings(cleaned);
        
        return cleaned;
    }
    
    /**
     * Remove markdown code blocks (```json ... ```)
     */
    public static String removeMarkdownCodeBlocks(String text) {
        if (text == null) return null;
        
        return text
            .replaceAll("(?s)```json\\s*", "")
            .replaceAll("(?s)```\\s*$", "")
            .replaceAll("(?s)^```\\s*", "")
            .trim();
    }
    
    /**
     * Extract JSON object from text that may contain surrounding content.
     * Finds the first { and last } to extract the JSON.
     * 
     * @param text Text potentially containing JSON
     * @return Extracted JSON string, or null if not found
     */
    public static String extractJsonObject(String text) {
        if (text == null) return null;
        
        String trimmed = text.trim();
        
        // If already starts with {, assume it's JSON
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        
        // Find JSON boundaries
        int jsonStart = trimmed.indexOf("{");
        int jsonEnd = trimmed.lastIndexOf("}");
        
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            String extracted = trimmed.substring(jsonStart, jsonEnd + 1);
            log.info("[AIResponseFixer] Extracted JSON from position {} to {}", jsonStart, jsonEnd);
            return extracted;
        }
        
        return null;
    }
    
    /**
     * Extract JSON array from text.
     * 
     * @param text Text potentially containing JSON array
     * @return Extracted JSON array string, or null if not found
     */
    public static String extractJsonArray(String text) {
        if (text == null) return null;
        
        String trimmed = text.trim();
        
        // If already starts with [, assume it's JSON array
        if (trimmed.startsWith("[")) {
            return trimmed;
        }
        
        // Find array boundaries
        int arrayStart = trimmed.indexOf("[");
        int arrayEnd = trimmed.lastIndexOf("]");
        
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            String extracted = trimmed.substring(arrayStart, arrayEnd + 1);
            log.info("[AIResponseFixer] Extracted JSON array from position {} to {}", arrayStart, arrayEnd);
            return extracted;
        }
        
        return null;
    }

    
    /**
     * Fix unquoted strings in JSON arrays (common Qwen issue).
     * 
     * Example: ["a", "b", unquoted text here] -> ["a", "b", "unquoted text here"]
     * Also handles: [unquoted1, unquoted2] -> ["unquoted1", "unquoted2"]
     * 
     * @param json JSON string with potential unquoted array elements
     * @return Fixed JSON string
     */
    public static String fixUnquotedArrayStrings(String json) {
        if (json == null) return null;
        
        // Pattern to find array content
        Pattern arrayPattern = Pattern.compile("\\[([^\\[\\]]*?)\\]", Pattern.DOTALL);
        Matcher matcher = arrayPattern.matcher(json);
        StringBuffer result = new StringBuffer();
        boolean anyFixed = false;
        
        while (matcher.find()) {
            String arrayContent = matcher.group(1);
            String fixedArray = fixArrayContent(arrayContent);
            
            if (!fixedArray.equals("[" + arrayContent + "]")) {
                anyFixed = true;
            }
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(fixedArray));
        }
        matcher.appendTail(result);
        
        if (anyFixed) {
            log.info("[AIResponseFixer] Fixed unquoted array strings in JSON");
        }
        
        return result.toString();
    }
    
    /**
     * Fix content of a single array.
     */
    private static String fixArrayContent(String arrayContent) {
        if (arrayContent == null || arrayContent.trim().isEmpty()) {
            return "[]";
        }
        
        String[] parts = arrayContent.split(",");
        StringBuilder fixedArray = new StringBuilder("[");
        boolean first = true;
        
        for (String part : parts) {
            if (!first) fixedArray.append(",");
            first = false;
            
            String trimmed = part.trim();
            
            // Skip empty parts
            if (trimmed.isEmpty()) {
                continue;
            }
            
            // If already quoted, a number, boolean, null, or nested object/array, keep as-is
            if (isValidJsonValue(trimmed)) {
                fixedArray.append(part);
            } else {
                // Unquoted string - add quotes and escape internal quotes
                String escaped = trimmed
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
                fixedArray.append("\"").append(escaped).append("\"");
            }
        }
        fixedArray.append("]");
        
        return fixedArray.toString();
    }
    
    /**
     * Check if a string is a valid JSON value (doesn't need quoting).
     */
    private static boolean isValidJsonValue(String value) {
        if (value == null) return false;
        
        String trimmed = value.trim();
        
        // Already quoted string
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return true;
        }
        
        // Number (integer or decimal)
        if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
            return true;
        }
        
        // Boolean or null
        if (trimmed.equals("true") || trimmed.equals("false") || trimmed.equals("null")) {
            return true;
        }
        
        // Nested object or array
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Try to parse JSON, applying fixes if initial parse fails.
     * Returns the fixed JSON string if successful, null otherwise.
     * 
     * @param response Raw AI response
     * @param objectMapper Jackson ObjectMapper for validation
     * @return Valid JSON string, or null if unfixable
     */
    public static String tryFixAndValidate(String response, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        if (response == null || response.isEmpty()) {
            return null;
        }
        
        // First try: parse as-is
        try {
            objectMapper.readTree(response);
            return response; // Already valid
        } catch (Exception e) {
            log.debug("[AIResponseFixer] Initial parse failed, attempting fixes...");
        }
        
        // Second try: clean and fix
        String fixed = cleanAndFixJson(response);
        if (fixed == null) {
            return null;
        }
        
        try {
            objectMapper.readTree(fixed);
            log.info("[AIResponseFixer] Successfully fixed JSON");
            return fixed;
        } catch (Exception e) {
            log.warn("[AIResponseFixer] Could not fix JSON: {}", e.getMessage());
            return null;
        }
    }
}
