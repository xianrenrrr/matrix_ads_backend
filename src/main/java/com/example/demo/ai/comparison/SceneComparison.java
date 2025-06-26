package com.example.demo.ai.comparison;

import java.util.Map;

public class SceneComparison {
    private int sceneIndex;
    private double similarity;
    private Map<String, Double> blockScores; // "row_col" → similarity score

    public SceneComparison() {
    }

    public SceneComparison(int sceneIndex, double similarity, Map<String, Double> blockScores) {
        this.sceneIndex = sceneIndex;
        this.similarity = similarity;
        this.blockScores = blockScores;
    }

    public int getSceneIndex() {
        return sceneIndex;
    }

    public void setSceneIndex(int sceneIndex) {
        this.sceneIndex = sceneIndex;
    }

    public double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(double similarity) {
        this.similarity = similarity;
    }

    public Map<String, Double> getBlockScores() {
        return blockScores;
    }

    public void setBlockScores(Map<String, Double> blockScores) {
        this.blockScores = blockScores;
    }

    @Override
    public String toString() {
        return String.format("SceneComparison{scene=%d, similarity=%.3f, blocks=%d}", 
                           sceneIndex, similarity, blockScores != null ? blockScores.size() : 0);
    }
}