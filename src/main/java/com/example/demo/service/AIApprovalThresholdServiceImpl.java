package com.example.demo.service;

import com.example.demo.dao.AIApprovalThresholdDao;
import com.example.demo.model.AIApprovalThreshold;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AIApprovalThresholdServiceImpl implements AIApprovalThresholdService {
    
    @Autowired
    private AIApprovalThresholdDao thresholdDao;
    
    @Override
    public String createThreshold(AIApprovalThreshold threshold) {
        validateThresholdConfiguration(threshold);
        return thresholdDao.save(threshold);
    }
    
    @Override
    public Optional<AIApprovalThreshold> getThresholdById(String id) {
        return thresholdDao.findById(id);
    }
    
    @Override
    public Optional<AIApprovalThreshold> getThresholdByTemplateId(String templateId) {
        return thresholdDao.findByTemplateId(templateId);
    }
    
    @Override
    public Optional<AIApprovalThreshold> getThresholdByTemplateAndManager(String templateId, String managerId) {
        return thresholdDao.findByTemplateIdAndManagerId(templateId, managerId);
    }
    
    @Override
    public List<AIApprovalThreshold> getThresholdsByManagerId(String managerId) {
        return thresholdDao.findByManagerId(managerId);
    }
    
    @Override
    public List<AIApprovalThreshold> getAllThresholds() {
        return thresholdDao.findAll();
    }
    
    @Override
    public void updateThreshold(AIApprovalThreshold threshold) {
        validateThresholdConfiguration(threshold);
        thresholdDao.update(threshold);
    }
    
    @Override
    public void deleteThreshold(String id) {
        thresholdDao.delete(id);
    }
    
    @Override
    public AIApprovalThreshold createDefaultThreshold(String templateId, String managerId) {
        AIApprovalThreshold threshold = new AIApprovalThreshold(templateId, managerId);
        String id = thresholdDao.save(threshold);
        threshold.setId(id);
        return threshold;
    }
    
    @Override
    public double getEffectiveThresholdForScene(String templateId, String sceneId) {
        Optional<AIApprovalThreshold> thresholdOpt = thresholdDao.findByTemplateId(templateId);
        if (thresholdOpt.isPresent()) {
            AIApprovalThreshold threshold = thresholdOpt.get();
            return threshold.getThresholdForScene(sceneId);
        }
        return 0.80; // Default threshold
    }
    
    @Override
    public boolean isAutoApprovalEnabledForTemplate(String templateId) {
        Optional<AIApprovalThreshold> thresholdOpt = thresholdDao.findByTemplateId(templateId);
        if (thresholdOpt.isPresent()) {
            return thresholdOpt.get().isAutoApprovalEnabled();
        }
        return true; // Default enabled
    }
    
    @Override
    public Map<String, Object> getAnalytics(String managerId) {
        Map<String, Object> analytics = new HashMap<>();
        
        List<AIApprovalThreshold> thresholds = thresholdDao.findByManagerId(managerId);
        
        // Basic stats
        analytics.put("totalTemplates", thresholds.size());
        analytics.put("autoApprovalEnabled", thresholds.stream()
                .mapToLong(t -> t.isAutoApprovalEnabled() ? 1 : 0).sum());
        
        // Average thresholds
        double avgGlobalThreshold = thresholds.stream()
                .mapToDouble(AIApprovalThreshold::getGlobalThreshold)
                .average().orElse(0.85);
        analytics.put("averageGlobalThreshold", avgGlobalThreshold);
        
        double avgSceneThreshold = thresholds.stream()
                .mapToDouble(AIApprovalThreshold::getSceneThreshold)
                .average().orElse(0.80);
        analytics.put("averageSceneThreshold", avgSceneThreshold);
        
        // Distribution of thresholds
        Map<String, Integer> thresholdDistribution = new HashMap<>();
        for (AIApprovalThreshold threshold : thresholds) {
            String range = getThresholdRange(threshold.getGlobalThreshold());
            thresholdDistribution.merge(range, 1, Integer::sum);
        }
        analytics.put("thresholdDistribution", thresholdDistribution);
        
        // Templates requiring manual review
        long manualReviewTemplates = thresholds.stream()
                .mapToLong(t -> t.getRequireManualReview().isEmpty() ? 0 : 1).sum();
        analytics.put("templatesWithManualReview", manualReviewTemplates);
        
        return analytics;
    }
    
    @Override
    public void validateThresholdConfiguration(AIApprovalThreshold threshold) {
        if (threshold == null) {
            throw new IllegalArgumentException("Threshold configuration cannot be null");
        }
        
        if (threshold.getTemplateId() == null || threshold.getTemplateId().trim().isEmpty()) {
            throw new IllegalArgumentException("Template ID is required");
        }
        
        if (threshold.getManagerId() == null || threshold.getManagerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Manager ID is required");
        }
        
        if (threshold.getGlobalThreshold() < 0.0 || threshold.getGlobalThreshold() > 1.0) {
            throw new IllegalArgumentException("Global threshold must be between 0.0 and 1.0");
        }
        
        if (threshold.getSceneThreshold() < 0.0 || threshold.getSceneThreshold() > 1.0) {
            throw new IllegalArgumentException("Scene threshold must be between 0.0 and 1.0");
        }
        
        if (threshold.getQualityThreshold() < 0.0 || threshold.getQualityThreshold() > 1.0) {
            throw new IllegalArgumentException("Quality threshold must be between 0.0 and 1.0");
        }
        
        if (threshold.getMaxAutoApprovals() < 0) {
            throw new IllegalArgumentException("Max auto approvals must be non-negative");
        }
        
        // Validate custom scene thresholds
        if (threshold.getCustomSceneThresholds() != null) {
            for (Map.Entry<String, Double> entry : threshold.getCustomSceneThresholds().entrySet()) {
                if (entry.getValue() < 0.0 || entry.getValue() > 1.0) {
                    throw new IllegalArgumentException(
                        "Scene threshold for " + entry.getKey() + " must be between 0.0 and 1.0");
                }
            }
        }
    }
    
    private String getThresholdRange(double threshold) {
        if (threshold < 0.6) return "Low (< 60%)";
        if (threshold < 0.75) return "Medium (60-75%)";
        if (threshold < 0.9) return "High (75-90%)";
        return "Very High (≥ 90%)";
    }
}