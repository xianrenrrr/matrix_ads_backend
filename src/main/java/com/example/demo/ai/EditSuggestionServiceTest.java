package com.example.demo.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Test class to demonstrate EditSuggestionService functionality
 * Run this to see sample suggestions generated from comparison data
 */
public class EditSuggestionServiceTest {

    public static void main(String[] args) {
        System.out.println("=== Edit Suggestion Service Test ===\n");

        // Create service instance
        EditSuggestionService service = new EditSuggestionService();

        // Test Case 1: Basic mismatch scenario
        testBasicMismatch(service);

        // Test Case 2: Multiple block mismatches
        testMultipleBlockMismatches(service);

        // Test Case 3: With example video descriptions
        testWithExampleVideo(service);
    }

    private static void testBasicMismatch(EditSuggestionService service) {
        System.out.println("--- Test Case 1: Basic Mismatch ---");

        EditSuggestionService.EditSuggestionRequest request = new EditSuggestionService.EditSuggestionRequest();

        // Template descriptions
        Map<String, String> template = new HashMap<>();
        template.put("0_0", "Close-up of a smiling woman looking at camera");
        template.put("0_1", "Company logo prominently displayed in background");
        template.put("1_1", "Professional hand gesture while speaking");

        // User video descriptions
        Map<String, String> user = new HashMap<>();
        user.put("0_0", "Wide shot of a man looking away from camera");
        user.put("0_1", "Blank white wall with no branding");
        user.put("1_1", "Person sitting with hands folded");

        // Similarity scores (low scores indicate poor matches)
        Map<String, Double> scores = new HashMap<>();
        scores.put("0_0", 0.42);
        scores.put("0_1", 0.30);
        scores.put("1_1", 0.55);

        request.setTemplateDescriptions(template);
        request.setUserDescriptions(user);
        request.setSimilarityScores(scores);
        request.setSceneNumber("1");

        EditSuggestionService.EditSuggestionResponse response = service.generateSuggestions(request);

        printResults(response);
        System.out.println();
    }

    private static void testMultipleBlockMismatches(EditSuggestionService service) {
        System.out.println("--- Test Case 2: Multiple Block Mismatches ---");

        EditSuggestionService.EditSuggestionRequest request = new EditSuggestionService.EditSuggestionRequest();

        // Template - a professional product demo
        Map<String, String> template = new HashMap<>();
        template.put("0_0", "Clean white background with soft lighting");
        template.put("0_1", "Professional woman in business attire");
        template.put("0_2", "Product prominently displayed on right");
        template.put("1_0", "Modern desk setup with laptop");
        template.put("1_1", "Woman engaging directly with camera");
        template.put("1_2", "Product features clearly visible");
        template.put("2_1", "Professional hand pointing to product");

        // User video - amateur setup
        Map<String, String> user = new HashMap<>();
        user.put("0_0", "Cluttered home office with harsh lighting");
        user.put("0_1", "Young man in casual t-shirt");
        user.put("0_2", "Product barely visible in corner");
        user.put("1_0", "Messy desk with personal items");
        user.put("1_1", "Man occasionally looking at notes");
        user.put("1_2", "Product obscured by other objects");
        user.put("2_1", "Hands gesturing randomly");

        // Low similarity scores across multiple blocks
        Map<String, Double> scores = new HashMap<>();
        scores.put("0_0", 0.25);
        scores.put("0_1", 0.35);
        scores.put("0_2", 0.20);
        scores.put("1_0", 0.30);
        scores.put("1_1", 0.45);
        scores.put("1_2", 0.15);
        scores.put("2_1", 0.40);

        request.setTemplateDescriptions(template);
        request.setUserDescriptions(user);
        request.setSimilarityScores(scores);
        request.setSceneNumber("1");

        EditSuggestionService.EditSuggestionResponse response = service.generateSuggestions(request);

        printResults(response);
        System.out.println();
    }

    private static void testWithExampleVideo(EditSuggestionService service) {
        System.out.println("--- Test Case 3: With Example Video ---");

        EditSuggestionService.EditSuggestionRequest request = new EditSuggestionService.EditSuggestionRequest();

        // Template
        Map<String, String> template = new HashMap<>();
        template.put("0_1", "Speaker centered in frame looking at camera");
        template.put("1_0", "Clear product showcase on left side");
        template.put("2_2", "Call-to-action text overlay in bottom right");

        // User video
        Map<String, String> user = new HashMap<>();
        user.put("0_1", "Speaker off-center looking down at phone");
        user.put("1_0", "Product partially visible and blurry");
        user.put("2_2", "No text overlay visible");

        // Example video (shows better execution)
        Map<String, String> example = new HashMap<>();
        example.put("0_1", "Professional speaker making eye contact with camera");
        example.put("1_0", "Product well-lit and clearly positioned on left");
        example.put("2_2", "Bold white text with clear call-to-action");

        // Similarity scores
        Map<String, Double> scores = new HashMap<>();
        scores.put("0_1", 0.48);
        scores.put("1_0", 0.32);
        scores.put("2_2", 0.18);

        request.setTemplateDescriptions(template);
        request.setUserDescriptions(user);
        request.setExampleDescriptions(example);
        request.setSimilarityScores(scores);
        request.setSceneNumber("2");

        EditSuggestionService.EditSuggestionResponse response = service.generateSuggestions(request);

        printResults(response);
        System.out.println();
    }

    private static void printResults(EditSuggestionService.EditSuggestionResponse response) {
        System.out.printf("Overall Similarity Score: %.2f%n", response.getOverallScore());
        System.out.println("Worst Performing Blocks: " + response.getWorstBlocks());
        System.out.println("\nGenerated Suggestions:");
        
        for (int i = 0; i < response.getSuggestions().size(); i++) {
            System.out.printf("%d. %s%n", i + 1, response.getSuggestions().get(i));
        }
    }
}