package com.example.demo.controller.contentmanager;

import com.example.demo.dao.TemplateDao;
import com.example.demo.dao.UserDao;
import com.example.demo.dao.GroupDao;
import com.example.demo.dao.SceneSubmissionDao;
import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Group;
import com.example.demo.model.SceneSubmission;
import com.example.demo.service.TemplateSubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/content-manager/templates")
public class ContentManager {
    @Autowired
    private com.google.cloud.firestore.Firestore db;
    
    @Autowired
    private SceneSubmissionDao sceneSubmissionDao;


    // --- Get single submitted video by composite ID ---
    @GetMapping("/submitted-videos/{compositeVideoId}")
    public ResponseEntity<Map<String, Object>> getSubmittedVideo(@PathVariable String compositeVideoId) throws Exception {
        try {
            // Get video document from submittedVideos collection
            com.google.cloud.firestore.DocumentReference videoDocRef = db.collection("submittedVideos").document(compositeVideoId);
            com.google.cloud.firestore.DocumentSnapshot videoDoc = videoDocRef.get().get();
            
            if (!videoDoc.exists()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Submitted video not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            
            Map<String, Object> videoData = new HashMap<>(videoDoc.getData());
            
            // Fetch full scene details from sceneSubmissions collection using scene IDs
            @SuppressWarnings("unchecked")
            Map<String, Object> scenes = (Map<String, Object>) videoData.get("scenes");
            if (scenes != null && !scenes.isEmpty()) {
                Map<String, Object> fullScenes = new HashMap<>();
                
                for (Map.Entry<String, Object> entry : scenes.entrySet()) {
                    String sceneNumber = entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> sceneRef = (Map<String, Object>) entry.getValue();
                    String sceneId = (String) sceneRef.get("sceneId");
                    
                    if (sceneId != null) {
                        // Fetch full scene data from sceneSubmissions collection
                        SceneSubmission sceneSubmission = sceneSubmissionDao.findById(sceneId);
                        if (sceneSubmission != null) {
                            Map<String, Object> fullSceneData = new HashMap<>();
                            fullSceneData.put("sceneId", sceneSubmission.getId());
                            fullSceneData.put("sceneNumber", sceneSubmission.getSceneNumber());
                            fullSceneData.put("sceneTitle", sceneSubmission.getSceneTitle());
                            fullSceneData.put("videoUrl", sceneSubmission.getVideoUrl());
                            fullSceneData.put("thumbnailUrl", sceneSubmission.getThumbnailUrl());
                            fullSceneData.put("status", sceneSubmission.getStatus());
                            fullSceneData.put("similarityScore", sceneSubmission.getSimilarityScore());
                            fullSceneData.put("aiSuggestions", sceneSubmission.getAiSuggestions());
                            fullSceneData.put("submittedAt", sceneSubmission.getSubmittedAt());
                            fullSceneData.put("originalFileName", sceneSubmission.getOriginalFileName());
                            fullSceneData.put("fileSize", sceneSubmission.getFileSize());
                            fullSceneData.put("format", sceneSubmission.getFormat());
                            fullScenes.put(sceneNumber, fullSceneData);
                        } else {
                            // Keep the minimal data if scene not found
                            fullScenes.put(sceneNumber, sceneRef);
                        }
                    }
                }
                
                videoData.put("scenes", fullScenes);
            }
            
            videoData.put("id", compositeVideoId); // Add document ID
            videoData.put("success", true);
            
            return ResponseEntity.ok(videoData);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get submitted video: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // --- Submissions grouped by status ---
    @GetMapping("/submissions")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> getAllSubmissions() throws Exception {
        com.google.cloud.firestore.CollectionReference submissionsRef = db.collection("submittedVideos");
        com.google.api.core.ApiFuture<com.google.cloud.firestore.QuerySnapshot> querySnapshot = submissionsRef.get();
        List<Map<String, Object>> pending = new ArrayList<>();
        List<Map<String, Object>> approved = new ArrayList<>();
        List<Map<String, Object>> published = new ArrayList<>();
        List<Map<String, Object>> rejected = new ArrayList<>();
        
        for (com.google.cloud.firestore.DocumentSnapshot doc : querySnapshot.get().getDocuments()) {
            Map<String, Object> data = doc.getData();
            if (data == null) continue;
            
            // Add document ID for frontend use
            data.put("id", doc.getId());
            
            String status = (String) data.getOrDefault("publishStatus", "pending");
            if ("approved".equalsIgnoreCase(status)) {
                approved.add(data);
            } else if ("published".equalsIgnoreCase(status)) {
                published.add(data);
            } else if ("rejected".equalsIgnoreCase(status)) {
                rejected.add(data);
            } else {
                pending.add(data);
            }
        }
        
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        result.put("pending", pending);
        result.put("approved", approved);
        result.put("published", published);
        result.put("rejected", rejected);
        return ResponseEntity.ok(result);
    }
    private final TemplateDao templateDao;
    private final UserDao userDao;
    private final GroupDao groupDao;
    
    @Autowired
    private TemplateSubscriptionService templateSubscriptionService;

    @Autowired
    public ContentManager(TemplateDao templateDao, UserDao userDao, GroupDao groupDao) {
        this.templateDao = templateDao;
        this.userDao = userDao;
        this.groupDao = groupDao;
    }
    

    @PostMapping
    public ResponseEntity<Map<String, Object>> createTemplate(@RequestBody com.example.demo.model.CreateTemplateRequest request) {
        String userId = request.getUserId();
        ManualTemplate manualTemplate = request.getManualTemplate();
        List<String> selectedGroupIds = request.getSelectedGroupIds();
        
        manualTemplate.setUserId(userId);
        
        try {
            // Create the template
            String templateId = templateDao.createTemplate(manualTemplate);
            userDao.addCreatedTemplate(userId, templateId); // Add templateId to created_template field in user doc
            
            // If groups are selected, assign the template to all members of those groups using batch subscription
            int totalMembersAssigned = 0;
            List<String> assignedGroupNames = new ArrayList<>();
            
            if (selectedGroupIds != null && !selectedGroupIds.isEmpty()) {
                // Use shared batch subscription service
                TemplateSubscriptionService.SubscriptionResult result = 
                    templateSubscriptionService.batchSubscribeToTemplate(templateId, selectedGroupIds);
                
                totalMembersAssigned = result.getTotalUsersAffected();
                assignedGroupNames = result.getProcessedGroups();
            }
            
            // Prepare response with assignment details
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("template", manualTemplate);
            response.put("templateId", templateId);
            response.put("assignedGroups", assignedGroupNames);
            response.put("totalMembersAssigned", totalMembersAssigned);
            response.put("message", "Template created successfully" + 
                (totalMembersAssigned > 0 ? " and assigned to " + totalMembersAssigned + " content creators across " + assignedGroupNames.size() + " groups" : ""));
            
            return new ResponseEntity<>(response, HttpStatus.CREATED);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to create template: " + e.getMessage());
            return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TemplateSummary>> getTemplatesByUserId(@PathVariable String userId) {
        try {
            List<ManualTemplate> templates = templateDao.getTemplatesByUserId(userId);
            System.out.println("Templates: " + templates);
            List<TemplateSummary> summaries = templates.stream()
                .map(t -> new TemplateSummary(t.getId(), t.getTemplateTitle()))
                .toList();
            return ResponseEntity.ok(summaries);
        } catch (InterruptedException | ExecutionException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // DTO for summary
    public static class TemplateSummary {
        private String id;
        private String templateTitle;
        public TemplateSummary(String id, String templateTitle) {
            this.id = id;
            this.templateTitle = templateTitle;
        }
        public String getId() { return id; }
        public String getTemplateTitle() { return templateTitle; }
        public void setId(String id) { this.id = id; }
        public void setTemplateTitle(String templateTitle) { this.templateTitle = templateTitle; }
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<ManualTemplate> getTemplateById(@PathVariable String templateId) {
        try {
            ManualTemplate template = templateDao.getTemplate(templateId);
            if (template != null) {
                return ResponseEntity.ok(template);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (InterruptedException | ExecutionException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String templateId, @RequestParam String userId) {
        try {
            boolean deleted = templateDao.deleteTemplate(templateId);
            if (deleted) {
                userDao.removeCreatedTemplate(userId, templateId); // Remove templateId from created_template field in user doc
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }


    // Get user information by ID
    @GetMapping("/users/{userId}")
    public ResponseEntity<UserInfo> getUserById(@PathVariable String userId) {
        try {
            com.example.demo.model.User user = userDao.findById(userId);
            if (user != null) {
                UserInfo userInfo = new UserInfo(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
                return ResponseEntity.ok(userInfo);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // DTO for user information
    public static class UserInfo {
        private String id;
        private String username;
        private String email;
        private String role;
        
        public UserInfo(String id, String username, String email, String role) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.role = role;
        }
        
        public String getId() { return id; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public String getRole() { return role; }
        public void setId(String id) { this.id = id; }
        public void setUsername(String username) { this.username = username; }
        public void setEmail(String email) { this.email = email; }
        public void setRole(String role) { this.role = role; }
    }

        // Get groups managed by a user
    @GetMapping("/groups/manager/{managerId}")
    public ResponseEntity<List<Map<String, Object>>> getGroupsByManager(@PathVariable String managerId) {
        try {
            List<Group> groups = groupDao.findByManagerId(managerId);
            List<Map<String, Object>> groupSummaries = new ArrayList<>();
            
            for (Group group : groups) {
                Map<String, Object> summary = new HashMap<>();
                summary.put("id", group.getId());
                summary.put("groupName", group.getGroupName());
                summary.put("memberCount", group.getMemberIds() != null ? group.getMemberIds().size() : 0);
                summary.put("description", group.getDescription());
                summary.put("createdAt", group.getCreatedAt());
                groupSummaries.add(summary);
            }
            
            return ResponseEntity.ok(groupSummaries);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

