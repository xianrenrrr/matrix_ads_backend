package com.example.demo.dao;

import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Video;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Repository
public class TemplateDaoImpl implements TemplateDao {

    @Autowired(required = false)
    private Firestore db;
    
    private void checkFirestore() {
        if (db == null) {
            throw new IllegalStateException("Firestore is not available in development mode. Please configure Firebase credentials or use a different data source.");
        }
    }


    @Override
    public String createTemplate(ManualTemplate template) throws ExecutionException, InterruptedException {
        checkFirestore();
        DocumentReference docRef = db.collection("templates").document();
        template.setId(docRef.getId()); // Assign generated ID to the template

        // If videoId is provided, ensure it's saved in the template
        if (template.getVideoId() != null && !template.getVideoId().isEmpty()) {
            // Verify the video exists
            DocumentReference videoRef = db.collection("videos").document(template.getVideoId());
            ApiFuture<DocumentSnapshot> videoFuture = videoRef.get();
            DocumentSnapshot videoDocument = videoFuture.get();

            if (videoDocument.exists()) {
                // Update video with template ID
                Video video = videoDocument.toObject(Video.class);
                video.setTemplateId(template.getId());
                videoRef.set(video).get();
            }
        }

        // Save the template
        ApiFuture<WriteResult> result = docRef.set(template);
        result.get(); // Wait for write to complete

        // --- Update user's templates field ---
        if (template.getUserId() != null && !template.getUserId().isEmpty()) {
            String userId = template.getUserId();
            DocumentReference userRef = db.collection("users").document(userId);
            db.runTransaction(transaction -> {
                DocumentSnapshot userSnap = transaction.get(userRef).get();
                List<String> templateIds = new ArrayList<>();
                if (userSnap.exists() && userSnap.contains("created_Templates")) {
                    Object raw = userSnap.get("created_Templates");
                    if (raw instanceof List<?>) {
                        for (Object obj : (List<?>) raw) {
                            if (obj instanceof String) {
                                templateIds.add((String) obj);
                            }
                        }
                    }
                }
                // If the user never had the field, templateIds will be empty and a new field will be created.
                // Use update with dot notation to add only the new templateId as a key to the map, preserving existing entries
                transaction.update(userRef, "created_Templates." + template.getId(), true);
                return null;
            }).get();
        }
        // --- End update user's templates field ---

        return template.getId();
    }

    @Override
    public ManualTemplate getTemplate(String id) throws ExecutionException, InterruptedException {
        checkFirestore();
        DocumentReference docRef = db.collection("templates").document(id);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            return document.toObject(ManualTemplate.class);
        } else {
            return null;
        }
    }

    public List<ManualTemplate> getTemplatesByUserId(String userId) throws ExecutionException, InterruptedException {
        // Fetch the user's templates list from users collection
        DocumentReference userRef = db.collection("users").document(userId);
        DocumentSnapshot userSnap = userRef.get().get();
        List<ManualTemplate> templates = new ArrayList<>();
        if (userSnap.exists() && userSnap.contains("created_Templates")) {
            Object raw = userSnap.get("created_Templates");
            List<String> templateIds = new ArrayList<>();
            if (raw instanceof Map<?, ?>) {
                for (Object key : ((Map<?, ?>) raw).keySet()) {
                    templateIds.add((String) key);
                }
            }
            // Batch fetch templates from templates collection using whereIn (10 at a time)
            List<ManualTemplate> templatesBatch = new ArrayList<>();
            for (int i = 0; i < templateIds.size(); i += 10) {
                List<String> batchIds = templateIds.subList(i, Math.min(i + 10, templateIds.size()));
                Query query = db.collection("templates").whereIn(FieldPath.documentId(), batchIds);
                List<QueryDocumentSnapshot> docs = query.get().get().getDocuments();
                for (QueryDocumentSnapshot doc : docs) {
                    ManualTemplate template = doc.toObject(ManualTemplate.class);
                    templatesBatch.add(template);
                }
            }
            templates.addAll(templatesBatch);
        }
        return templates;
    }

    @Override
    public List<ManualTemplate> getAllTemplates() throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = db.collection("templates").get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        List<ManualTemplate> templates = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            ManualTemplate template = document.toObject(ManualTemplate.class);
            templates.add(template);
        }
        return templates;
    }

    @Override
    public boolean updateTemplate(String templateId, ManualTemplate manualTemplate) throws ExecutionException, InterruptedException {
        if (templateId == null || templateId.isEmpty()) {
            throw new IllegalArgumentException("Template ID must not be null or empty for update.");
        }
        manualTemplate.setId(templateId); // Ensure object has the correct ID
        DocumentReference docRef = db.collection("templates").document(templateId);
        ApiFuture<WriteResult> result = docRef.set(manualTemplate);
        result.get(); // Wait for write to complete
        return true;
    }

    @Override
    public List<ManualTemplate> getTemplatesAssignedToGroup(String groupId) throws ExecutionException, InterruptedException {
        checkFirestore();
        System.out.println("DEBUG: Getting templates assigned to group: " + groupId);
        
        // Fast approach: Get assignedTemplates from group document
        DocumentReference groupRef = db.collection("groups").document(groupId);
        DocumentSnapshot groupDoc = groupRef.get().get();
        
        if (!groupDoc.exists()) {
            System.out.println("DEBUG: Group " + groupId + " does not exist");
            return new ArrayList<>();
        }
        
        // Get the assignedTemplates array from the group
        @SuppressWarnings("unchecked")
        List<String> templateIds = (List<String>) groupDoc.get("assignedTemplates");
        System.out.println("DEBUG: Group " + groupId + " has assignedTemplates: " + templateIds);
        
        if (templateIds == null || templateIds.isEmpty()) {
            System.out.println("DEBUG: No templates assigned to group " + groupId);
            return new ArrayList<>();
        }
        
        // Fetch all templates by their IDs
        List<ManualTemplate> templates = new ArrayList<>();
        for (String templateId : templateIds) {
            System.out.println("DEBUG: Fetching template: " + templateId);
            ManualTemplate template = getTemplate(templateId);
            if (template != null) {
                templates.add(template);
                System.out.println("DEBUG: Found template: " + template.getTemplateTitle());
            } else {
                System.out.println("DEBUG: Template " + templateId + " not found");
            }
        }
        
        System.out.println("DEBUG: Returning " + templates.size() + " templates for group " + groupId);
        return templates;
    }

    @Override
    public boolean deleteTemplate(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Template ID must not be null or empty for delete.");
        }
        try {
            db.collection("templates").document(id).delete().get(); // Wait until delete completes
            return true; // Successful deletion
        } catch (Exception e) {
            e.printStackTrace();
            return false; // Deletion failed
        }
    }

}