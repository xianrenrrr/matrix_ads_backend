package com.example.demo.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

public class AIApprovalThreshold {
    private String id;
    private String templateId;
    private String managerId;
    private double globalThreshold = 0.85;
    private double sceneThreshold = 0.80;
    private double qualityThreshold = 0.70;
    private boolean autoApprovalEnabled = true;
    private boolean allowManualOverride = true;
    private int maxAutoApprovals = 100;
    private Map<String, Double> customSceneThresholds = new HashMap<>();
    private Set<String> requireManualReview = new HashSet<>();
    private List<String> requiredQualityChecks = new ArrayList<>();
    
    public AIApprovalThreshold() {}
    
    public AIApprovalThreshold(String templateId, String managerId) {
        this.templateId = templateId;
        this.managerId = managerId;
        initializeDefaults();
    }
    
    private void initializeDefaults() {
        this.requiredQualityChecks.add("Lighting Quality");
        this.requiredQualityChecks.add("Audio Clarity");
        this.requiredQualityChecks.add("Person Position");
        this.requiredQualityChecks.add("Background Compliance");
        this.requiredQualityChecks.add("Script Accuracy");
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getTemplateId() {
        return templateId;
    }
    
    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }
    
    public String getManagerId() {
        return managerId;
    }
    
    public void setManagerId(String managerId) {
        this.managerId = managerId;
    }
    
    public double getGlobalThreshold() {
        return globalThreshold;
    }
    
    public void setGlobalThreshold(double globalThreshold) {
        this.globalThreshold = globalThreshold;
    }
    
    public double getSceneThreshold() {
        return sceneThreshold;
    }
    
    public void setSceneThreshold(double sceneThreshold) {
        this.sceneThreshold = sceneThreshold;
    }
    
    public double getQualityThreshold() {
        return qualityThreshold;
    }
    
    public void setQualityThreshold(double qualityThreshold) {
        this.qualityThreshold = qualityThreshold;
    }
    
    public boolean isAutoApprovalEnabled() {
        return autoApprovalEnabled;
    }
    
    public void setAutoApprovalEnabled(boolean autoApprovalEnabled) {
        this.autoApprovalEnabled = autoApprovalEnabled;
    }
    
    public boolean isAllowManualOverride() {
        return allowManualOverride;
    }
    
    public void setAllowManualOverride(boolean allowManualOverride) {
        this.allowManualOverride = allowManualOverride;
    }
    
    public int getMaxAutoApprovals() {
        return maxAutoApprovals;
    }
    
    public void setMaxAutoApprovals(int maxAutoApprovals) {
        this.maxAutoApprovals = maxAutoApprovals;
    }
    
    public Map<String, Double> getCustomSceneThresholds() {
        return customSceneThresholds;
    }
    
    public void setCustomSceneThresholds(Map<String, Double> customSceneThresholds) {
        this.customSceneThresholds = customSceneThresholds;
    }
    
    public Set<String> getRequireManualReview() {
        return requireManualReview;
    }
    
    public void setRequireManualReview(Set<String> requireManualReview) {
        this.requireManualReview = requireManualReview;
    }
    
    public List<String> getRequiredQualityChecks() {
        return requiredQualityChecks;
    }
    
    public void setRequiredQualityChecks(List<String> requiredQualityChecks) {
        this.requiredQualityChecks = requiredQualityChecks;
    }
    
    // Helper methods
    public double getThresholdForScene(String sceneId) {
        return customSceneThresholds.getOrDefault(sceneId, sceneThreshold);
    }
    
    public boolean requiresManualReview(String sceneId) {
        return requireManualReview.contains(sceneId);
    }
    
    public void setSceneThreshold(String sceneId, double threshold) {
        customSceneThresholds.put(sceneId, threshold);
    }
    
    public void addManualReviewRequirement(String sceneId) {
        requireManualReview.add(sceneId);
    }
    
    public void removeManualReviewRequirement(String sceneId) {
        requireManualReview.remove(sceneId);
    }
}