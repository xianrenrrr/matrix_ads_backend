package com.example.demo.ai.services;

import java.util.ArrayList;
import java.util.List;

/**
 * Result from Qwen-based scene comparison
 */
public class ComparisonResult {
    
    private int score;  // 0-100 similarity score (for backward compatibility)
    private int similarityScore;  // 0-100 overall score (new direct comparison)
    private List<String> suggestions;  // Improvement suggestions
    private List<String> matchedObjects;  // Matched key elements
    private List<String> missingObjects;  // Missing key elements
    
    public ComparisonResult() {
        this.suggestions = new ArrayList<>();
        this.matchedObjects = new ArrayList<>();
        this.missingObjects = new ArrayList<>();
    }
    
    public ComparisonResult(int score, List<String> suggestions) {
        this.score = score;
        this.similarityScore = score;
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
        this.matchedObjects = new ArrayList<>();
        this.missingObjects = new ArrayList<>();
    }
    
    public static ComparisonResult error(String errorMessage) {
        ComparisonResult result = new ComparisonResult();
        result.setScore(0);
        result.setSimilarityScore(0);
        result.setSuggestions(List.of(errorMessage));
        return result;
    }
    
    // Getters and Setters
    
    public int getScore() {
        return score;
    }
    
    public void setScore(int score) {
        this.score = score;
        this.similarityScore = score;  // Keep in sync
    }
    
    public int getSimilarityScore() {
        return similarityScore;
    }
    
    public void setSimilarityScore(int similarityScore) {
        this.similarityScore = similarityScore;
        this.score = similarityScore;  // Keep in sync
    }
    
    public List<String> getSuggestions() {
        return suggestions;
    }
    
    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }
    
    public List<String> getMatchedObjects() {
        return matchedObjects;
    }
    
    public void setMatchedObjects(List<String> matchedObjects) {
        this.matchedObjects = matchedObjects;
    }
    
    public List<String> getMissingObjects() {
        return missingObjects;
    }
    
    public void setMissingObjects(List<String> missingObjects) {
        this.missingObjects = missingObjects;
    }
    
    @Override
    public String toString() {
        return "ComparisonResult{score=" + score + ", matched=" + matchedObjects.size() + 
               ", missing=" + missingObjects.size() + ", suggestions=" + suggestions.size() + " items}";
    }
}
