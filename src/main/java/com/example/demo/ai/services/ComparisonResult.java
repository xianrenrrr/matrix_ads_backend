package com.example.demo.ai.services;

import java.util.ArrayList;
import java.util.List;

/**
 * Result from Qwen-based scene comparison
 */
public class ComparisonResult {
    
    private int score;  // 0-100 similarity score
    private List<String> suggestions;  // Improvement suggestions
    
    public ComparisonResult() {
        this.suggestions = new ArrayList<>();
    }
    
    public ComparisonResult(int score, List<String> suggestions) {
        this.score = score;
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
    }
    
    // Getters and Setters
    
    public int getScore() {
        return score;
    }
    
    public void setScore(int score) {
        this.score = score;
    }
    
    public List<String> getSuggestions() {
        return suggestions;
    }
    
    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }
    
    @Override
    public String toString() {
        return "ComparisonResult{score=" + score + ", suggestions=" + suggestions.size() + " items}";
    }
}
