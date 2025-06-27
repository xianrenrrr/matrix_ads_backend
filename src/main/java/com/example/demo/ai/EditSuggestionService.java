package com.example.demo.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating edit suggestions based on video-template comparison results
 * Uses AI to provide actionable feedback for improving video similarity to templates
 */
@Service
public class EditSuggestionService {

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String openaiApiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EditSuggestionService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generate edit suggestions based on comparison results
     */
    public EditSuggestionResponse generateSuggestions(EditSuggestionRequest request) {
        try {
            // Identify blocks with lowest similarity scores
            List<BlockMismatch> mismatches = identifyWorstMatches(
                request.getTemplateDescriptions(),
                request.getUserDescriptions(), 
                request.getSimilarityScores(),
                request.getExampleDescriptions()
            );

            // Generate AI-powered suggestions
            List<String> suggestions = generateAISuggestions(mismatches, request);

            return EditSuggestionResponse.builder()
                .suggestions(suggestions)
                .overallScore(calculateOverallScore(request.getSimilarityScores()))
                .worstBlocks(mismatches.stream()
                    .limit(3)
                    .map(m -> m.getBlockId())
                    .collect(Collectors.toList()))
                .build();

        } catch (Exception e) {
            System.err.println("Error generating suggestions: " + e.getMessage());
            return EditSuggestionResponse.builder()
                .suggestions(Arrays.asList("Unable to generate suggestions at this time. Please try again."))
                .overallScore(0.5)
                .worstBlocks(Arrays.asList())
                .build();
        }
    }

    /**
     * Identify blocks with the worst similarity scores
     */
    private List<BlockMismatch> identifyWorstMatches(
            Map<String, String> templateDescriptions,
            Map<String, String> userDescriptions,
            Map<String, Double> similarityScores,
            Map<String, String> exampleDescriptions) {

        List<BlockMismatch> mismatches = new ArrayList<>();

        for (Map.Entry<String, Double> entry : similarityScores.entrySet()) {
            String blockId = entry.getKey();
            Double score = entry.getValue();

            // Focus on blocks with low similarity (< 0.7)
            if (score < 0.7) {
                BlockMismatch mismatch = BlockMismatch.builder()
                    .blockId(blockId)
                    .similarityScore(score)
                    .templateDescription(templateDescriptions.get(blockId))
                    .userDescription(userDescriptions.get(blockId))
                    .exampleDescription(exampleDescriptions != null ? exampleDescriptions.get(blockId) : null)
                    .blockPosition(getBlockPosition(blockId))
                    .build();

                mismatches.add(mismatch);
            }
        }

        // Sort by worst similarity scores first
        mismatches.sort(Comparator.comparing(BlockMismatch::getSimilarityScore));

        return mismatches.stream().limit(5).collect(Collectors.toList());
    }

    /**
     * Convert block ID to human-readable position
     */
    private String getBlockPosition(String blockId) {
        String[] parts = blockId.split("_");
        if (parts.length != 2) return blockId;

        int row = Integer.parseInt(parts[0]);
        int col = Integer.parseInt(parts[1]);

        String[] rowNames = {"top", "middle", "bottom"};
        String[] colNames = {"left", "center", "right"};

        return rowNames[row] + "-" + colNames[col];
    }

    /**
     * Generate AI-powered suggestions using GPT-4o
     */
    private List<String> generateAISuggestions(List<BlockMismatch> mismatches, EditSuggestionRequest request) {
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            return generateFallbackSuggestions(mismatches);
        }

        try {
            String prompt = buildPrompt(mismatches, request);
            String response = callOpenAI(prompt);
            return parseAISuggestions(response);

        } catch (Exception e) {
            System.err.println("AI suggestion generation failed: " + e.getMessage());
            return generateFallbackSuggestions(mismatches);
        }
    }

    /**
     * Build prompt for AI suggestion generation
     */
    private String buildPrompt(List<BlockMismatch> mismatches, EditSuggestionRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert video editor helping a content creator improve their video to match a template better.\n\n");
        
        prompt.append("CONTEXT:\n");
        prompt.append("- The user's video was compared to a template using AI\n");
        prompt.append("- Each video frame is divided into a 3x3 grid (9 blocks total)\n");
        prompt.append("- Similarity scores range from 0.0 (completely different) to 1.0 (identical)\n\n");

        prompt.append("COMPARISON RESULTS:\n");
        for (BlockMismatch mismatch : mismatches) {
            prompt.append(String.format("Block %s (%s):\n", mismatch.getBlockId(), mismatch.getBlockPosition()));
            prompt.append(String.format("  Template: \"%s\"\n", mismatch.getTemplateDescription()));
            prompt.append(String.format("  User Video: \"%s\"\n", mismatch.getUserDescription()));
            if (mismatch.getExampleDescription() != null) {
                prompt.append(String.format("  Example: \"%s\"\n", mismatch.getExampleDescription()));
            }
            prompt.append(String.format("  Similarity Score: %.2f\n\n", mismatch.getSimilarityScore()));
        }

        prompt.append("TASK:\n");
        prompt.append("Generate 3-5 specific, actionable suggestions to help the user improve their video.\n");
        prompt.append("Each suggestion should:\n");
        prompt.append("- Be specific about which part of the frame needs improvement\n");
        prompt.append("- Explain what the template expects vs. what the user currently has\n");
        prompt.append("- Provide concrete steps to fix the issue\n");
        prompt.append("- Be encouraging and constructive\n\n");

        prompt.append("Format each suggestion as a numbered list item (1., 2., etc.).\n");
        prompt.append("Focus on the most impactful changes that will improve similarity scores.");

        return prompt.toString();
    }

    /**
     * Call OpenAI API
     */
    private String callOpenAI(String prompt) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o");
        requestBody.put("max_tokens", 800);
        requestBody.put("temperature", 0.7);
        
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        requestBody.put("messages", messages);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(openaiApiUrl, entity, String.class);

        JsonNode jsonResponse = objectMapper.readTree(response.getBody());
        return jsonResponse.path("choices").get(0).path("message").path("content").asText();
    }

    /**
     * Parse AI response into suggestion list
     */
    private List<String> parseAISuggestions(String response) {
        List<String> suggestions = new ArrayList<>();
        String[] lines = response.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.matches("^\\d+\\..*")) {
                // Remove the number prefix and add to suggestions
                suggestions.add(line.replaceFirst("^\\d+\\.", "").trim());
            }
        }

        // Ensure we have 3-5 suggestions
        if (suggestions.size() < 3) {
            suggestions.addAll(generateFallbackSuggestions(null).subList(0, Math.min(3, 5 - suggestions.size())));
        }

        return suggestions.stream().limit(5).collect(Collectors.toList());
    }

    /**
     * Generate fallback suggestions when AI is unavailable
     */
    private List<String> generateFallbackSuggestions(List<BlockMismatch> mismatches) {
        List<String> suggestions = new ArrayList<>();

        if (mismatches != null && !mismatches.isEmpty()) {
            for (BlockMismatch mismatch : mismatches.stream().limit(3).collect(Collectors.toList())) {
                String suggestion = String.format(
                    "In the %s area (block %s): The template expects \"%s\" but your video shows \"%s\". " +
                    "Consider repositioning your camera or adjusting the framing to better match the template.",
                    mismatch.getBlockPosition(),
                    mismatch.getBlockId(),
                    mismatch.getTemplateDescription(),
                    mismatch.getUserDescription()
                );
                suggestions.add(suggestion);
            }
        }

        // Add generic suggestions if needed
        if (suggestions.size() < 3) {
            suggestions.add("Review the template's framing and adjust your camera position to match the expected composition.");
            suggestions.add("Ensure your lighting and background match the template's requirements for better similarity.");
            suggestions.add("Pay attention to the positioning of objects and people in each section of the frame.");
        }

        return suggestions.stream().limit(5).collect(Collectors.toList());
    }

    /**
     * Calculate overall similarity score
     */
    private double calculateOverallScore(Map<String, Double> similarityScores) {
        if (similarityScores.isEmpty()) return 0.0;
        
        return similarityScores.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
    }

    // Data Transfer Objects
    public static class EditSuggestionRequest {
        private Map<String, String> templateDescriptions;
        private Map<String, String> userDescriptions;
        private Map<String, Double> similarityScores;
        private Map<String, String> exampleDescriptions;
        private String sceneNumber;

        // Getters and Setters
        public Map<String, String> getTemplateDescriptions() { return templateDescriptions; }
        public void setTemplateDescriptions(Map<String, String> templateDescriptions) { this.templateDescriptions = templateDescriptions; }
        
        public Map<String, String> getUserDescriptions() { return userDescriptions; }
        public void setUserDescriptions(Map<String, String> userDescriptions) { this.userDescriptions = userDescriptions; }
        
        public Map<String, Double> getSimilarityScores() { return similarityScores; }
        public void setSimilarityScores(Map<String, Double> similarityScores) { this.similarityScores = similarityScores; }
        
        public Map<String, String> getExampleDescriptions() { return exampleDescriptions; }
        public void setExampleDescriptions(Map<String, String> exampleDescriptions) { this.exampleDescriptions = exampleDescriptions; }
        
        public String getSceneNumber() { return sceneNumber; }
        public void setSceneNumber(String sceneNumber) { this.sceneNumber = sceneNumber; }
    }

    public static class EditSuggestionResponse {
        private List<String> suggestions;
        private double overallScore;
        private List<String> worstBlocks;

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private List<String> suggestions;
            private double overallScore;
            private List<String> worstBlocks;

            public Builder suggestions(List<String> suggestions) { this.suggestions = suggestions; return this; }
            public Builder overallScore(double overallScore) { this.overallScore = overallScore; return this; }
            public Builder worstBlocks(List<String> worstBlocks) { this.worstBlocks = worstBlocks; return this; }

            public EditSuggestionResponse build() {
                EditSuggestionResponse response = new EditSuggestionResponse();
                response.suggestions = this.suggestions;
                response.overallScore = this.overallScore;
                response.worstBlocks = this.worstBlocks;
                return response;
            }
        }

        // Getters and Setters
        public List<String> getSuggestions() { return suggestions; }
        public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
        
        public double getOverallScore() { return overallScore; }
        public void setOverallScore(double overallScore) { this.overallScore = overallScore; }
        
        public List<String> getWorstBlocks() { return worstBlocks; }
        public void setWorstBlocks(List<String> worstBlocks) { this.worstBlocks = worstBlocks; }
    }

    private static class BlockMismatch {
        private String blockId;
        private double similarityScore;
        private String templateDescription;
        private String userDescription;
        private String exampleDescription;
        private String blockPosition;

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String blockId;
            private double similarityScore;
            private String templateDescription;
            private String userDescription;
            private String exampleDescription;
            private String blockPosition;

            public Builder blockId(String blockId) { this.blockId = blockId; return this; }
            public Builder similarityScore(double similarityScore) { this.similarityScore = similarityScore; return this; }
            public Builder templateDescription(String templateDescription) { this.templateDescription = templateDescription; return this; }
            public Builder userDescription(String userDescription) { this.userDescription = userDescription; return this; }
            public Builder exampleDescription(String exampleDescription) { this.exampleDescription = exampleDescription; return this; }
            public Builder blockPosition(String blockPosition) { this.blockPosition = blockPosition; return this; }

            public BlockMismatch build() {
                BlockMismatch mismatch = new BlockMismatch();
                mismatch.blockId = this.blockId;
                mismatch.similarityScore = this.similarityScore;
                mismatch.templateDescription = this.templateDescription;
                mismatch.userDescription = this.userDescription;
                mismatch.exampleDescription = this.exampleDescription;
                mismatch.blockPosition = this.blockPosition;
                return mismatch;
            }
        }

        // Getters
        public String getBlockId() { return blockId; }
        public double getSimilarityScore() { return similarityScore; }
        public String getTemplateDescription() { return templateDescription; }
        public String getUserDescription() { return userDescription; }
        public String getExampleDescription() { return exampleDescription; }
        public String getBlockPosition() { return blockPosition; }
    }
}