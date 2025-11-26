package com.example.demo.dao;

import com.example.demo.model.TemplateAssignment;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.gson.Gson;
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
    
    @Autowired
    private ManagerSubmissionDao managerSubmissionDao;
    
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
        
        System.out.println("✅ Loaded " + assignments.size() + " active assignments for group: " + groupId);
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
        // Get assignment first to find managerId for cleanup
        TemplateAssignment assignment = getAssignment(assignmentId);
        
        // Delete the assignment
        db.collection(COLLECTION_NAME).document(assignmentId).delete().get();
        
        // Clean up related submissions from managerSubmissions
        if (assignment != null && assignment.getPushedBy() != null) {
            try {
                managerSubmissionDao.deleteByAssignmentId(assignment.getPushedBy(), assignmentId);
                System.out.println("[CASCADE] Deleted submissions for assignment: " + assignmentId + " from manager: " + assignment.getPushedBy());
            } catch (Exception e) {
                System.err.println("[CASCADE] Failed to delete submissions for assignment: " + assignmentId + " - " + e.getMessage());
            }
        }
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
    
    @Override
    public void deleteAssignmentsByTemplate(String templateId) throws Exception {
        // Get all assignments for this template
        QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME)
            .whereEqualTo("masterTemplateId", templateId)
            .get()
            .get();
        
        // Delete each assignment and its related submissions
        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
            String assignmentId = doc.getId();
            String managerId = doc.getString("pushedBy");
            
            // Delete the assignment
            doc.getReference().delete().get();
            
            // Clean up related submissions from managerSubmissions
            if (managerId != null) {
                try {
                    managerSubmissionDao.deleteByAssignmentId(managerId, assignmentId);
                    System.out.println("[CASCADE] Deleted submissions for assignment: " + assignmentId);
                } catch (Exception e) {
                    System.err.println("[CASCADE] Failed to delete submissions for assignment: " + assignmentId + " - " + e.getMessage());
                }
            }
        }
        
        System.out.println("[CASCADE] Deleted " + querySnapshot.size() + " assignments for template: " + templateId);
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
        
        // Get templateSnapshot field specifically
        Object templateSnapshotData = doc.get("templateSnapshot");
        if (templateSnapshotData != null) {
            try {
                // Use Gson with proper configuration for Firestore Timestamp objects
                com.google.gson.GsonBuilder gsonBuilder = new com.google.gson.GsonBuilder();
                
                // Register custom deserializer for Date fields (handles Firestore Timestamps)
                gsonBuilder.registerTypeAdapter(java.util.Date.class, new com.google.gson.JsonDeserializer<java.util.Date>() {
                    @Override
                    public java.util.Date deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type typeOfT, 
                                                      com.google.gson.JsonDeserializationContext context) {
                        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                            // Handle string dates
                            try {
                                return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(json.getAsString());
                            } catch (Exception e) {
                                return null;
                            }
                        } else if (json.isJsonObject()) {
                            // Handle Firestore Timestamp objects {seconds: ..., nanoseconds: ...}
                            com.google.gson.JsonObject obj = json.getAsJsonObject();
                            if (obj.has("seconds")) {
                                long seconds = obj.get("seconds").getAsLong();
                                return new java.util.Date(seconds * 1000);
                            }
                        } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
                            // Handle epoch milliseconds
                            return new java.util.Date(json.getAsLong());
                        }
                        return null;
                    }
                });
                
                com.google.gson.Gson gson = gsonBuilder.create();
                
                String json = gson.toJson(templateSnapshotData);
                
                // Log for debugging
                System.out.println("=== DESERIALIZING TEMPLATE SNAPSHOT ===");
                System.out.println("Assignment ID: " + doc.getId());
                System.out.println("JSON length: " + json.length());
                System.out.println("JSON preview: " + (json.length() > 300 ? json.substring(0, 300) + "..." : json));
                
                com.example.demo.model.ManualTemplate snapshot = 
                    gson.fromJson(json, com.example.demo.model.ManualTemplate.class);
                
                if (snapshot != null) {
                    System.out.println("✅ Snapshot deserialized successfully");
                    System.out.println("   - Template ID: " + snapshot.getId());
                    System.out.println("   - Title: " + snapshot.getTemplateTitle());
                    System.out.println("   - Scenes: " + (snapshot.getScenes() != null ? snapshot.getScenes().size() : "null"));
                    System.out.println("   - Total duration: " + snapshot.getTotalVideoLength());
                    
                    if (snapshot.getScenes() != null && !snapshot.getScenes().isEmpty()) {
                        System.out.println("   - First scene: " + snapshot.getScenes().get(0).getSceneTitle());
                        System.out.println("   - First scene duration: " + snapshot.getScenes().get(0).getSceneDurationInSeconds());
                    }
                } else {
                    System.err.println("❌ Snapshot is null after deserialization");
                }
                
                assignment.setTemplateSnapshot(snapshot);
            } catch (Exception e) {
                System.err.println("❌ Error converting templateSnapshot: " + e.getMessage());
                e.printStackTrace();
                // Set null if conversion fails
                assignment.setTemplateSnapshot(null);
            }
        } else {
            System.err.println("⚠️  templateSnapshot field is null in Firestore document: " + doc.getId());
        }
        
        assignment.setPushedAt(doc.getDate("pushedAt"));
        assignment.setExpiresAt(doc.getDate("expiresAt"));
        
        Long durationDays = doc.getLong("durationDays");
        assignment.setDurationDays(durationDays != null ? durationDays.intValue() : null);
        
        assignment.setPushedBy(doc.getString("pushedBy"));
        assignment.setLastRenewed(doc.getDate("lastRenewed"));
        
        return assignment;
    }
}
