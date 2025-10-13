package com.example.demo.dao;

import com.example.demo.model.TemplateAssignment;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Repository
public class TemplateAssignmentDaoImpl implements TemplateAssignmentDao {
    
    @Autowired
    private Firestore db;
    
    private static final String COLLECTION_NAME = "templateAssignments";
    
    @Override
    public String createAssignment(TemplateAssignment assignment) throws Exception {
        DocumentReference docRef = db.collection(COLLECTION_NAME).document();
        assignment.setId(docRef.getId());
        
        Map<String, Object> data = assignmentToMap(assignment);
        docRef.set(data).get();
        
        return assignment.getId();
    }
    
    @Override
    public TemplateAssignment getAssignment(String assignmentId) throws Exception {
        DocumentSnapshot doc = db.collection(COLLECTION_NAME).document(assignmentId).get().get();
        if (!doc.exists()) {
            return null;
        }
        return mapToAssignment(doc);
    }
    
    @Override
    public List<TemplateAssignment> getAssignmentsByTemplate(String templateId) throws Exception {
        QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME)
            .whereEqualTo("masterTemplateId", templateId)
            .get()
            .get();
        
        List<TemplateAssignment> assignments = new ArrayList<>();
        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
            assignments.add(mapToAssignment(doc));
        }
        return assignments;
    }
    
    @Override
    public List<TemplateAssignment> getAssignmentsByGroup(String groupId) throws Exception {
        QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME)
            .whereEqualTo("groupId", groupId)
            .get()
            .get();
        
        // Filter out expired assignments in memory
        List<TemplateAssignment> assignments = new ArrayList<>();
        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
            TemplateAssignment assignment = mapToAssignment(doc);
            if (!assignment.isExpired()) {
                assignments.add(assignment);
            }
        }
        return assignments;
    }
    
    @Override
    public List<TemplateAssignment> getExpiredAssignments() throws Exception {
        Date now = new Date();
        QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME)
            .whereLessThan("expiresAt", now)
            .get()
            .get();
        
        List<TemplateAssignment> assignments = new ArrayList<>();
        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
            TemplateAssignment assignment = mapToAssignment(doc);
            if (assignment.isExpired()) {
                assignments.add(assignment);
            }
        }
        return assignments;
    }
    
    @Override
    public List<TemplateAssignment> getExpiringSoonAssignments(int daysThreshold) throws Exception {
        // Get all assignments and filter in memory
        QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME)
            .get()
            .get();
        
        List<TemplateAssignment> assignments = new ArrayList<>();
        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
            TemplateAssignment assignment = mapToAssignment(doc);
            if (!assignment.isExpired()) {
                long daysUntilExpiry = assignment.getDaysUntilExpiry();
                if (daysUntilExpiry > 0 && daysUntilExpiry <= daysThreshold) {
                    assignments.add(assignment);
                }
            }
        }
        return assignments;
    }
    
    @Override
    public void updateAssignment(TemplateAssignment assignment) throws Exception {
        Map<String, Object> data = assignmentToMap(assignment);
        db.collection(COLLECTION_NAME).document(assignment.getId()).set(data).get();
    }
    
    @Override
    public void deleteAssignment(String assignmentId) throws Exception {
        db.collection(COLLECTION_NAME).document(assignmentId).delete().get();
    }
    
    @Override
    public boolean hasActiveAssignment(String templateId, String groupId) throws Exception {
        QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME)
            .whereEqualTo("masterTemplateId", templateId)
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("status", "active")
            .get()
            .get();
        
        return !querySnapshot.isEmpty();
    }
    
    // Helper methods
    private Map<String, Object> assignmentToMap(TemplateAssignment assignment) {
        Map<String, Object> data = new HashMap<>();
        data.put("masterTemplateId", assignment.getMasterTemplateId());
        data.put("groupId", assignment.getGroupId());
        data.put("templateSnapshot", assignment.getTemplateSnapshot());
        data.put("pushedAt", assignment.getPushedAt());
        data.put("expiresAt", assignment.getExpiresAt());
        data.put("durationDays", assignment.getDurationDays());
        data.put("pushedBy", assignment.getPushedBy());
        data.put("lastRenewed", assignment.getLastRenewed());
        return data;
    }
    
    private TemplateAssignment mapToAssignment(DocumentSnapshot doc) {
        TemplateAssignment assignment = new TemplateAssignment();
        assignment.setId(doc.getId());
        assignment.setMasterTemplateId(doc.getString("masterTemplateId"));
        assignment.setGroupId(doc.getString("groupId"));
        assignment.setTemplateSnapshot(doc.toObject(com.example.demo.model.ManualTemplate.class));
        assignment.setPushedAt(doc.getDate("pushedAt"));
        assignment.setExpiresAt(doc.getDate("expiresAt"));
        
        Long durationDays = doc.getLong("durationDays");
        assignment.setDurationDays(durationDays != null ? durationDays.intValue() : null);
        
        assignment.setPushedBy(doc.getString("pushedBy"));
        assignment.setLastRenewed(doc.getDate("lastRenewed"));
        
        assignment.updateStatus();
        return assignment;
    }
}
