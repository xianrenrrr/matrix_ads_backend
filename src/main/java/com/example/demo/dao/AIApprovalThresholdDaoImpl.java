package com.example.demo.dao;

import com.example.demo.model.AIApprovalThreshold;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Repository
public class AIApprovalThresholdDaoImpl implements AIApprovalThresholdDao {
    
    private static final String COLLECTION_NAME = "ai_approval_thresholds";
    
    @Autowired
    private Firestore db;
    
    @Override
    public String save(AIApprovalThreshold threshold) {
        try {
            CollectionReference collection = db.collection(COLLECTION_NAME);
            
            if (threshold.getId() == null || threshold.getId().isEmpty()) {
                DocumentReference docRef = collection.document();
                threshold.setId(docRef.getId());
            }
            
            Map<String, Object> data = convertToMap(threshold);
            collection.document(threshold.getId()).set(data).get();
            
            return threshold.getId();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error saving AI approval threshold", e);
        }
    }
    
    @Override
    public Optional<AIApprovalThreshold> findById(String id) {
        try {
            DocumentSnapshot doc = db.collection(COLLECTION_NAME).document(id).get().get();
            if (doc.exists()) {
                return Optional.of(convertToObject(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error finding AI approval threshold by id", e);
        }
    }
    
    @Override
    public Optional<AIApprovalThreshold> findByTemplateId(String templateId) {
        try {
            QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME)
                    .whereEqualTo("templateId", templateId)
                    .limit(1)
                    .get().get();
            
            if (!querySnapshot.isEmpty()) {
                return Optional.of(convertToObject(querySnapshot.getDocuments().get(0)));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error finding AI approval threshold by template id", e);
        }
    }
    
    @Override
    public Optional<AIApprovalThreshold> findByTemplateIdAndManagerId(String templateId, String managerId) {
        try {
            QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME)
                    .whereEqualTo("templateId", templateId)
                    .whereEqualTo("managerId", managerId)
                    .limit(1)
                    .get().get();
            
            if (!querySnapshot.isEmpty()) {
                return Optional.of(convertToObject(querySnapshot.getDocuments().get(0)));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error finding AI approval threshold by template and manager id", e);
        }
    }
    
    @Override
    public List<AIApprovalThreshold> findByManagerId(String managerId) {
        try {
            QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME)
                    .whereEqualTo("managerId", managerId)
                    .get().get();
            
            List<AIApprovalThreshold> thresholds = new ArrayList<>();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                thresholds.add(convertToObject(doc));
            }
            return thresholds;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error finding AI approval thresholds by manager id", e);
        }
    }
    
    @Override
    public List<AIApprovalThreshold> findAll() {
        try {
            QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME).get().get();
            List<AIApprovalThreshold> thresholds = new ArrayList<>();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                thresholds.add(convertToObject(doc));
            }
            return thresholds;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error finding all AI approval thresholds", e);
        }
    }
    
    @Override
    public void update(AIApprovalThreshold threshold) {
        try {
            Map<String, Object> data = convertToMap(threshold);
            db.collection(COLLECTION_NAME).document(threshold.getId()).set(data).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error updating AI approval threshold", e);
        }
    }
    
    @Override
    public void delete(String id) {
        try {
            db.collection(COLLECTION_NAME).document(id).delete().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error deleting AI approval threshold", e);
        }
    }
    
    @Override
    public boolean existsByTemplateId(String templateId) {
        try {
            QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME)
                    .whereEqualTo("templateId", templateId)
                    .limit(1)
                    .get().get();
            
            return !querySnapshot.isEmpty();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error checking if AI approval threshold exists by template id", e);
        }
    }
    
    private Map<String, Object> convertToMap(AIApprovalThreshold threshold) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", threshold.getId());
        data.put("templateId", threshold.getTemplateId());
        data.put("managerId", threshold.getManagerId());
        data.put("globalThreshold", threshold.getGlobalThreshold());
        data.put("sceneThreshold", threshold.getSceneThreshold());
        data.put("qualityThreshold", threshold.getQualityThreshold());
        data.put("autoApprovalEnabled", threshold.isAutoApprovalEnabled());
        data.put("allowManualOverride", threshold.isAllowManualOverride());
        data.put("maxAutoApprovals", threshold.getMaxAutoApprovals());
        data.put("customSceneThresholds", threshold.getCustomSceneThresholds());
        data.put("requireManualReview", new ArrayList<>(threshold.getRequireManualReview()));
        data.put("requiredQualityChecks", threshold.getRequiredQualityChecks());
        return data;
    }
    
    private AIApprovalThreshold convertToObject(DocumentSnapshot doc) {
        AIApprovalThreshold threshold = new AIApprovalThreshold();
        threshold.setId(doc.getId());
        threshold.setTemplateId(doc.getString("templateId"));
        threshold.setManagerId(doc.getString("managerId"));
        
        if (doc.contains("globalThreshold") && doc.getDouble("globalThreshold") != null) {
            threshold.setGlobalThreshold(doc.getDouble("globalThreshold"));
        }
        if (doc.contains("sceneThreshold") && doc.getDouble("sceneThreshold") != null) {
            threshold.setSceneThreshold(doc.getDouble("sceneThreshold"));
        }
        if (doc.contains("qualityThreshold") && doc.getDouble("qualityThreshold") != null) {
            threshold.setQualityThreshold(doc.getDouble("qualityThreshold"));
        }
        if (doc.contains("autoApprovalEnabled") && doc.getBoolean("autoApprovalEnabled") != null) {
            threshold.setAutoApprovalEnabled(doc.getBoolean("autoApprovalEnabled"));
        }
        if (doc.contains("allowManualOverride") && doc.getBoolean("allowManualOverride") != null) {
            threshold.setAllowManualOverride(doc.getBoolean("allowManualOverride"));
        }
        if (doc.contains("maxAutoApprovals") && doc.getLong("maxAutoApprovals") != null) {
            threshold.setMaxAutoApprovals(doc.getLong("maxAutoApprovals").intValue());
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> customThresholds = (Map<String, Object>) doc.get("customSceneThresholds");
        if (customThresholds != null) {
            Map<String, Double> sceneThresholds = new HashMap<>();
            for (Map.Entry<String, Object> entry : customThresholds.entrySet()) {
                sceneThresholds.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
            }
            threshold.setCustomSceneThresholds(sceneThresholds);
        }
        
        @SuppressWarnings("unchecked")
        List<String> manualReviewList = (List<String>) doc.get("requireManualReview");
        if (manualReviewList != null) {
            threshold.setRequireManualReview(new HashSet<>(manualReviewList));
        }
        
        @SuppressWarnings("unchecked")
        List<String> qualityChecks = (List<String>) doc.get("requiredQualityChecks");
        if (qualityChecks != null) {
            threshold.setRequiredQualityChecks(new ArrayList<>(qualityChecks));
        }
        
        return threshold;
    }
}