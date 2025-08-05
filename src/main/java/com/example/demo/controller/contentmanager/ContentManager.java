package com.example.demo.controller.contentmanager;

import com.example.demo.dao.TemplateDao;
import com.example.demo.dao.UserDao;
import com.example.demo.dao.GroupDao;
import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Group;
import com.google.cloud.firestore.*;
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


    // --- Submissions grouped by status ---
    @GetMapping("/submissions")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> getAllSubmissions() throws Exception {
        com.google.cloud.firestore.CollectionReference submissionsRef = db.collection("submittedVideos");
        com.google.api.core.ApiFuture<com.google.cloud.firestore.QuerySnapshot> querySnapshot = submissionsRef.get();
        List<Map<String, Object>> pending = new ArrayList<>();
        List<Map<String, Object>> approved = new ArrayList<>();
        List<Map<String, Object>> rejected = new ArrayList<>();
        for (com.google.cloud.firestore.DocumentSnapshot doc : querySnapshot.get().getDocuments()) {
            Map<String, Object> data = doc.getData();
            if (data == null) continue;
            String status = (String) data.getOrDefault("publishStatus", "pending");
            if ("approved".equalsIgnoreCase(status)) {
                approved.add(data);
            } else if ("rejected".equalsIgnoreCase(status)) {
                rejected.add(data);
            } else {
                pending.add(data);
            }
        }
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        result.put("pending", pending);
        result.put("approved", approved);
        result.put("rejected", rejected);
        return ResponseEntity.ok(result);
    }
    private final TemplateDao templateDao;
    private final UserDao userDao;
    private final GroupDao groupDao;

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
            
            // If groups are selected, assign the template to all members of those groups
            int totalMembersAssigned = 0;
            List<String> assignedGroupNames = new ArrayList<>();
            
            if (selectedGroupIds != null && !selectedGroupIds.isEmpty()) {
                for (String groupId : selectedGroupIds) {
                    Group group = groupDao.findById(groupId);
                    if (group != null && group.getMemberIds() != null) {
                        assignedGroupNames.add(group.getGroupName());
                        
                        // Subscribe all group members to this template
                        for (String memberId : group.getMemberIds()) {
                            try {
                                userDao.addSubscribedTemplate(memberId, templateId);
                                totalMembersAssigned++;
                            } catch (Exception e) {
                                System.err.println("Failed to assign template to user " + memberId + ": " + e.getMessage());
                            }
                        }
                    }
                }
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

}

