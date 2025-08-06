package com.example.demo.dao;

import com.example.demo.model.AIApprovalThreshold;
import java.util.List;
import java.util.Optional;

public interface AIApprovalThresholdDao {
    String save(AIApprovalThreshold threshold);
    Optional<AIApprovalThreshold> findById(String id);
    Optional<AIApprovalThreshold> findByTemplateId(String templateId);
    Optional<AIApprovalThreshold> findByTemplateIdAndManagerId(String templateId, String managerId);
    List<AIApprovalThreshold> findByManagerId(String managerId);
    List<AIApprovalThreshold> findAll();
    void update(AIApprovalThreshold threshold);
    void delete(String id);
    boolean existsByTemplateId(String templateId);
}