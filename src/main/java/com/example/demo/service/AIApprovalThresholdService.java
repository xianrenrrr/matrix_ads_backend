package com.example.demo.service;

import com.example.demo.model.AIApprovalThreshold;
import java.util.List;
import java.util.Optional;
import java.util.Map;

public interface AIApprovalThresholdService {
    String createThreshold(AIApprovalThreshold threshold);
    Optional<AIApprovalThreshold> getThresholdById(String id);
    Optional<AIApprovalThreshold> getThresholdByTemplateId(String templateId);
    Optional<AIApprovalThreshold> getThresholdByTemplateAndManager(String templateId, String managerId);
    List<AIApprovalThreshold> getThresholdsByManagerId(String managerId);
    List<AIApprovalThreshold> getAllThresholds();
    void updateThreshold(AIApprovalThreshold threshold);
    void deleteThreshold(String id);
    
    // Business logic methods
    AIApprovalThreshold createDefaultThreshold(String templateId, String managerId);
    double getEffectiveThresholdForScene(String templateId, String sceneId);
    boolean isAutoApprovalEnabledForTemplate(String templateId);
    Map<String, Object> getAnalytics(String managerId);
    void validateThresholdConfiguration(AIApprovalThreshold threshold);
}