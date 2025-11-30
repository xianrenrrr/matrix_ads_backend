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
 * - Trailing commas in objects/arrays
 * - Single quotes instead of double quotes
 * - Unescaped special characters in strings
 * - Comments in JSON
 * - Truncated JSON (missing closing brackets)
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
            log.debug("[AIResponseFixer] Input is null or empty");
            return null;
        }
        
        log.debug("[AIResponseFixer] Input length: {}, preview: {}", 
            response.length(), 
            response.substring(0, Math.min(200, response.length())).replaceAll("\\s+", " "));
        
        String cleaned = response;
        
        // Step 1: Remove markdown code blocks (multiple patterns)
        cleaned = removeMarkdownCodeBlocks(cleaned);
        
        // Step 2: Remove comments
        cleaned = removeJsonComments(cleaned);
        
        // Step 3: Extract JSON from surrounding text
        cleaned = extractJsonObject(cleaned);
        
        if (cleaned == null) {
            // Try extracting array if object not found
            cleaned = extractJsonArray(response);
            if (cleaned == null) {
                log.warn("[AIResponseFixer] No JSON object or array found in response");
                return null;
            }
        }
        
        // Step 4: Fix common JSON issues
        cleaned = fixTrailingCommas(cleaned);
        cleaned = fixSingleQuotes(cleaned);
        cleaned = fixUnquotedArrayStrings(cleaned);
        cleaned = fixUnescapedNewlines(cleaned);
        cleaned = fixTruncatedJson(cleaned);
        
        log.debug("[AIResponseFixer] Output length: {}", cleaned.length());
        
        return cleaned;
    }
    
    /**
     * Remove markdown code blocks (```json ... ```)
     * Handles multiple variations:
     * - ```json ... ```
     * - ``` ... ```
     * - ```JSON ... ```
     * - With or without newlines
     */
    public static String removeMarkdownCodeBlocks(String text) {
        if (text == null) return null;
        
        String result = text;
        
        // Pattern 1: ```json\n...\n``` (with language specifier)
        result = result.replaceAll("(?s)```(?:json|JSON)?\\s*\\n?", "");
        
        // Pattern 2: Remaining ``` at end
        result = result.replaceAll("(?s)\\n?```\\s*$", "");
        
        // Pattern 3: ``` at start (without language)
        result = result.replaceAll("(?s)^\\s*```\\s*\\n?", "");
        
        return result.trim();
    }
    
    /**
     * Remove JSON comments (single-line // and multi-line block comments)
     */
    public static String removeJsonComments(String text) {
        if (text == null) return null;
        
        // Remove single-line comments (but not inside strings)
        // This is a simplified version - may not handle all edge cases
        String result = text.replaceAll("(?m)^\\s*//.*$", "");
        
        // Remove multi-line comments
        result = result.replaceAll("(?s)/\\*.*?\\*/", "");
        
        return result;
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
        
        // Number (integer or decimal, including scientific notation)
        if (trimmed.matches("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")) {
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
     * Fix trailing commas in JSON objects and arrays.
     * Example: {"a": 1,} -> {"a": 1}
     * Example: [1, 2,] -> [1, 2]
     */
    public static String fixTrailingCommas(String json) {
        if (json == null) return null;
        
        // Remove trailing commas before } or ]
        String result = json.replaceAll(",\\s*}", "}");
        result = result.replaceAll(",\\s*]", "]");
        
        return result;
    }
    
    /**
     * Fix single quotes to double quotes in JSON.
     * Example: {'key': 'value'} -> {"key": "value"}
     * Be careful not to replace single quotes inside double-quoted strings.
     */
    public static String fixSingleQuotes(String json) {
        if (json == null) return null;
        
        // Simple approach: replace single quotes that look like JSON delimiters
        // This pattern matches: 'key': or : 'value' or ['item']
        StringBuilder result = new StringBuilder();
        boolean inDoubleQuote = false;
        boolean inSingleQuote = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            char prev = i > 0 ? json.charAt(i - 1) : 0;
            
            if (c == '"' && prev != '\\') {
                inDoubleQuote = !inDoubleQuote;
                result.append(c);
            } else if (c == '\'' && !inDoubleQuote) {
                // Replace single quote with double quote when not inside a double-quoted string
                result.append('"');
                inSingleQuote = !inSingleQuote;
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    /**
     * Fix unescaped newlines inside JSON strings.
     * Newlines inside string values must be escaped as \n
     */
    public static String fixUnescapedNewlines(String json) {
        if (json == null) return null;
        
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            char prev = i > 0 ? json.charAt(i - 1) : 0;
            
            if (c == '"' && prev != '\\') {
                inString = !inString;
                result.append(c);
            } else if (inString && c == '\n') {
                result.append("\\n");
            } else if (inString && c == '\r') {
                result.append("\\r");
            } else if (inString && c == '\t') {
                result.append("\\t");
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    /**
     * Fix truncated JSON by adding missing closing brackets.
     * This is a best-effort fix for when AI response is cut off.
     */
    public static String fixTruncatedJson(String json) {
        if (json == null) return null;
        
        int openBraces = 0;
        int openBrackets = 0;
        boolean inString = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            char prev = i > 0 ? json.charAt(i - 1) : 0;
            
            if (c == '"' && prev != '\\') {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') openBraces++;
                else if (c == '}') openBraces--;
                else if (c == '[') openBrackets++;
                else if (c == ']') openBrackets--;
            }
        }
        
        // Add missing closing brackets
        StringBuilder result = new StringBuilder(json);
        
        // If we're in the middle of a string, close it
        if (inString) {
            result.append("\"");
            log.info("[AIResponseFixer] Added missing closing quote");
        }
        
        // Add missing brackets
        for (int i = 0; i < openBrackets; i++) {
            result.append("]");
        }
        for (int i = 0; i < openBraces; i++) {
            result.append("}");
        }
        
        if (openBraces > 0 || openBrackets > 0) {
            log.info("[AIResponseFixer] Added {} closing braces and {} closing brackets", openBraces, openBrackets);
        }
        
        return result.toString();
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
    
    /**
     * Parse AI response to Map with multiple fallback strategies.
     * This is the recommended method for parsing AI JSON responses.
     * 
     * @param response Raw AI response
     * @param objectMapper Jackson ObjectMapper
     * @return Parsed Map, or null if all strategies fail
     */
    public static java.util.Map<String, Object> parseToMap(String response, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        if (response == null || response.isEmpty()) {
            log.warn("[AIResponseFixer] parseToMap: input is null or empty");
            return null;
        }
        
        // Strategy 1: Try parsing as-is
        try {
            java.util.Map<String, Object> result = objectMapper.readValue(response, 
                new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
            log.debug("[AIResponseFixer] parseToMap: Strategy 1 (as-is) succeeded");
            return result;
        } catch (Exception e) {
            log.debug("[AIResponseFixer] parseToMap: Strategy 1 failed: {}", e.getMessage());
        }
        
        // Strategy 2: Clean and fix JSON
        String fixed = cleanAndFixJson(response);
        if (fixed != null) {
            try {
                java.util.Map<String, Object> result = objectMapper.readValue(fixed, 
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
                log.info("[AIResponseFixer] parseToMap: Strategy 2 (cleanAndFixJson) succeeded");
                return result;
            } catch (Exception e) {
                log.debug("[AIResponseFixer] parseToMap: Strategy 2 failed: {}", e.getMessage());
            }
        }
        
        // Strategy 3: Try extracting just the JSON object part
        String extracted = extractJsonObject(response);
        if (extracted != null && !extracted.equals(fixed)) {
            try {
                java.util.Map<String, Object> result = objectMapper.readValue(extracted, 
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
                log.info("[AIResponseFixer] parseToMap: Strategy 3 (extractJsonObject) succeeded");
                return result;
            } catch (Exception e) {
                log.debug("[AIResponseFixer] parseToMap: Strategy 3 failed: {}", e.getMessage());
            }
        }
        
        // Strategy 4: Try with aggressive fixes
        String aggressive = aggressiveFix(response);
        if (aggressive != null) {
            try {
                java.util.Map<String, Object> result = objectMapper.readValue(aggressive, 
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
                log.info("[AIResponseFixer] parseToMap: Strategy 4 (aggressiveFix) succeeded");
                return result;
            } catch (Exception e) {
                log.debug("[AIResponseFixer] parseToMap: Strategy 4 failed: {}", e.getMessage());
            }
        }
        
        log.warn("[AIResponseFixer] parseToMap: All strategies failed for response (first 200 chars): {}", 
            response.substring(0, Math.min(200, response.length())).replaceAll("\\s+", " "));
        return null;
    }
    
    /**
     * Aggressive JSON fix - tries harder to extract valid JSON.
     * Use when standard fixes fail.
     */
    public static String aggressiveFix(String response) {
        if (response == null) return null;
        
        String result = response;
        
        // Remove all markdown formatting
        result = result.replaceAll("```[a-zA-Z]*\\s*", "");
        result = result.replaceAll("```", "");
        
        // Remove common AI prefixes
        result = result.replaceAll("(?i)^\\s*(here'?s?|the|my|this is|output:?|result:?|json:?)\\s*(the\\s+)?(json|response|output)?:?\\s*", "");
        
        // Remove trailing explanations after JSON
        int lastBrace = result.lastIndexOf("}");
        if (lastBrace > 0 && lastBrace < result.length() - 1) {
            result = result.substring(0, lastBrace + 1);
        }
        
        // Extract JSON object
        result = extractJsonObject(result);
        if (result == null) return null;
        
        // Apply all fixes
        result = fixTrailingCommas(result);
        result = fixSingleQuotes(result);
        result = fixUnescapedNewlines(result);
        result = fixTruncatedJson(result);
        
        // Remove any remaining control characters
        result = result.replaceAll("[\\x00-\\x1F\\x7F]", " ");
        
        return result;
    }
}
