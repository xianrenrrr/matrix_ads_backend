package com.example.demo.ai.comparison;

import java.util.List;

public class ComparisonResult {
    private double overallScore;
    private List<SceneComparison> sceneComparisons;

    public ComparisonResult() {
    }

    public ComparisonResult(double overallScore, List<SceneComparison> sceneComparisons) {
        this.overallScore = overallScore;
        this.sceneComparisons = sceneComparisons;
    }

    public double getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(double overallScore) {
        this.overallScore = overallScore;
    }

    public List<SceneComparison> getSceneComparisons() {
        return sceneComparisons;
    }

    public void setSceneComparisons(List<SceneComparison> sceneComparisons) {
        this.sceneComparisons = sceneComparisons;
    }

    @Override
    public String toString() {
        return String.format("ComparisonResult{overallScore=%.3f, scenes=%d}", 
                           overallScore, sceneComparisons != null ? sceneComparisons.size() : 0);
    }
}