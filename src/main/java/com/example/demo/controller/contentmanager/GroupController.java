package com.example.demo.controller.contentmanager;

import com.example.demo.dao.InviteDao;
import com.example.demo.dao.UserDao;
import com.example.demo.model.Invite;
import com.example.demo.model.User;
import com.example.demo.api.ApiResponse;
import com.example.demo.service.I18nService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/content-manager/groups")
@CrossOrigin(origins = {"http://localhost:4040", "https://matrix-ads-frontend.onrender.com"})
public class GroupController {

    @Autowired
    private InviteDao inviteDao; // Using InviteDao for group management

    @Autowired
    private UserDao userDao;
    
    @Autowired
    private I18nService i18nService;

    // 1. Create Group/Invite
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createGroup(@RequestBody Map<String, Object> requestBody,
                                                                        @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        String managerId = (String) requestBody.get("managerId");
        String groupName = (String) requestBody.get("groupName");
        String description = (String) requestBody.get("description");

        // Validate required fields
        if (managerId == null || groupName == null || groupName.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required fields: managerId and groupName");
        }

        // Verify manager exists and has correct role
        User manager = userDao.findById(managerId);
        if (manager == null || !"content_manager".equals(manager.getRole())) {
            throw new IllegalArgumentException("Invalid manager or insufficient permissions");
        }

        // Check if group name already exists for this manager
        List<Invite> existingGroups = inviteDao.findByManagerId(managerId);
        for (Invite existing : existingGroups) {
            if (groupName.equals(existing.getGroupName()) && "active".equals(existing.getStatus())) {
                throw new IllegalArgumentException("Group with this name already exists");
            }
        }

            // Generate unique token for joining
            String token = "group_" + UUID.randomUUID().toString().replace("-", "");

            // Create permanent group (no expiration)
            Invite group = new Invite();
            group.setManagerId(managerId);
            group.setManagerName(manager.getUsername() != null ? manager.getUsername() : manager.getEmail());
            group.setGroupName(groupName);
            group.setDescription(description);
            group.setRole("content_creator");
            group.setToken(token);
            group.setStatus("active"); // Groups are always active
            group.setCreatedAt(new Date());
            group.setUpdatedAt(new Date());
            group.setExpiresAt(null); // No expiration for groups
            group.setMemberIds(new ArrayList<>());
            group.setMemberCount(0);
            
            // Set default AI settings
            group.setAiApprovalThreshold(0.85);
            group.setAiAutoApprovalEnabled(true);
            group.setAllowManualOverride(true);

            inviteDao.save(group);

            // Generate QR code and invite URLs
            String qrCodeUrl = generateQRCodeUrl(token);
            String inviteUrl = generateInviteUrl(token);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("id", group.getId());
            responseData.put("groupName", group.getGroupName());
            responseData.put("description", group.getDescription());
            responseData.put("managerId", group.getManagerId());
            responseData.put("token", group.getToken());
            responseData.put("qrCodeUrl", qrCodeUrl);
            responseData.put("inviteUrl", inviteUrl);
            responseData.put("memberCount", group.getMemberCount());
            responseData.put("aiApprovalThreshold", group.getAiApprovalThreshold());
            responseData.put("aiAutoApprovalEnabled", group.isAiAutoApprovalEnabled());
            responseData.put("createdAt", group.getCreatedAt());

        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }

    // 2. List Manager's Groups
    @GetMapping("/manager/{managerId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getGroupsByManager(@PathVariable String managerId,
                                                                                      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        List<Invite> groups = inviteDao.findByManagerId(managerId);
        List<Map<String, Object>> groupSummaries = new ArrayList<>();

        for (Invite group : groups) {
            if ("active".equals(group.getStatus())) {
                Map<String, Object> summary = new HashMap<>();
                summary.put("id", group.getId());
                summary.put("groupName", group.getGroupName());
                summary.put("description", group.getDescription());
                summary.put("memberCount", group.getMemberCount());
                summary.put("aiApprovalThreshold", group.getAiApprovalThreshold());
                summary.put("aiAutoApprovalEnabled", group.isAiAutoApprovalEnabled());
                summary.put("allowManualOverride", group.isAllowManualOverride());
                summary.put("createdAt", group.getCreatedAt());
                summary.put("updatedAt", group.getUpdatedAt());
                groupSummaries.add(summary);
            }
        }

        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, groupSummaries));
    }

    // 3. Get Group QR Code
    @GetMapping("/{groupId}/qrcode")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGroupQRCode(@PathVariable String groupId,
                                                                            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        Invite group = inviteDao.findById(groupId);
        if (group == null || !"active".equals(group.getStatus())) {
            throw new NoSuchElementException("Group not found with ID: " + groupId);
        }

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("groupId", group.getId());
            responseData.put("groupName", group.getGroupName());
            responseData.put("token", group.getToken());
            responseData.put("qrCodeUrl", generateQRCodeUrl(group.getToken()));
            responseData.put("inviteUrl", generateInviteUrl(group.getToken()));
            responseData.put("miniProgramPath", "pages/invite/invite?token=" + group.getToken());

        String message = i18nService.getMessage("qr.generated", language);
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }

    // 4. Delete Group
    @DeleteMapping("/{groupId}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable String groupId,
                                                         @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        Invite group = inviteDao.findById(groupId);
        if (group == null) {
            throw new NoSuchElementException("Group not found with ID: " + groupId);
        }

        inviteDao.delete(groupId);

        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message));
    }

    // 5. Add Member to Group
    @PostMapping("/{groupId}/members")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addMember(
            @PathVariable String groupId,
            @RequestBody Map<String, Object> requestBody,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        String userId = (String) requestBody.get("userId");
        String userEmail = (String) requestBody.get("userEmail");

        if (userId == null && userEmail == null) {
            throw new IllegalArgumentException("Either userId or userEmail is required");
        }

        Invite group = inviteDao.findById(groupId);
        if (group == null || !"active".equals(group.getStatus())) {
            throw new NoSuchElementException("Group not found with ID: " + groupId);
        }

        // Find user by ID or email
        User user = null;
        if (userId != null) {
            user = userDao.findById(userId);
        } else {
            user = userDao.findByEmail(userEmail);
        }

        if (user == null) {
            throw new NoSuchElementException("User not found");
        }

        // Add member to group
        group.addMember(user.getId());
        inviteDao.update(group);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("memberCount", group.getMemberCount());
        String message = i18nService.getMessage("user.joined.group", language);
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }

    // 6. Remove Member from Group
    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeMember(
            @PathVariable String groupId,
            @PathVariable String userId,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        Invite group = inviteDao.findById(groupId);
        if (group == null || !"active".equals(group.getStatus())) {
            throw new NoSuchElementException("Group not found with ID: " + groupId);
        }

        group.removeMember(userId);
        inviteDao.update(group);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("memberCount", group.getMemberCount());
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }

    // 7. Get Group Members
    @GetMapping("/{groupId}/members")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getGroupMembers(@PathVariable String groupId,
                                                                                   @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        Invite group = inviteDao.findById(groupId);
        if (group == null || !"active".equals(group.getStatus())) {
            throw new NoSuchElementException("Group not found with ID: " + groupId);
        }

            List<Map<String, Object>> members = new ArrayList<>();
            for (String memberId : group.getMemberIds()) {
                User user = userDao.findById(memberId);
                if (user != null) {
                    Map<String, Object> memberInfo = new HashMap<>();
                    memberInfo.put("userId", user.getId());
                    memberInfo.put("email", user.getEmail());
                    memberInfo.put("username", user.getUsername());
                    memberInfo.put("joinedAt", group.getAcceptedAt()); // TODO: Track individual join dates
                    members.add(memberInfo);
                }
            }

        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, members));
    }

    // 8. Update Group AI Settings
    @PutMapping("/{groupId}/ai-settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateGroupAISettings(
            @PathVariable String groupId,
            @RequestBody Map<String, Object> aiSettings,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        Invite group = inviteDao.findById(groupId);
        if (group == null || !"active".equals(group.getStatus())) {
            throw new NoSuchElementException("Group not found with ID: " + groupId);
        }

            // Update AI settings
            if (aiSettings.containsKey("aiApprovalThreshold")) {
                Object thresholdObj = aiSettings.get("aiApprovalThreshold");
                double threshold = thresholdObj instanceof Number ? 
                    ((Number) thresholdObj).doubleValue() : 
                    Double.parseDouble(thresholdObj.toString());
                group.setAiApprovalThreshold(threshold);
            }
            if (aiSettings.containsKey("aiAutoApprovalEnabled")) {
                group.setAiAutoApprovalEnabled((Boolean) aiSettings.get("aiAutoApprovalEnabled"));
            }
            if (aiSettings.containsKey("allowManualOverride")) {
                group.setAllowManualOverride((Boolean) aiSettings.get("allowManualOverride"));
            }
            if (aiSettings.containsKey("sceneThresholds")) {
                @SuppressWarnings("unchecked")
                Map<String, Double> sceneThresholds = (Map<String, Double>) aiSettings.get("sceneThresholds");
                group.setSceneThresholds(sceneThresholds);
            }

            inviteDao.update(group);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("aiApprovalThreshold", group.getAiApprovalThreshold());
            responseData.put("aiAutoApprovalEnabled", group.isAiAutoApprovalEnabled());
            responseData.put("allowManualOverride", group.isAllowManualOverride());
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }

    // 9. Get Group AI Settings
    @GetMapping("/{groupId}/ai-settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGroupAISettings(@PathVariable String groupId,
                                                                               @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        Invite group = inviteDao.findById(groupId);
        if (group == null || !"active".equals(group.getStatus())) {
            throw new NoSuchElementException("Group not found with ID: " + groupId);
        }

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("groupId", group.getId());
            responseData.put("groupName", group.getGroupName());
            responseData.put("aiSettings", Map.of(
                "aiApprovalThreshold", group.getAiApprovalThreshold(),
                "aiAutoApprovalEnabled", group.isAiAutoApprovalEnabled(),
                "allowManualOverride", group.isAllowManualOverride(),
                "sceneThresholds", group.getSceneThresholds()
            ));

        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }

    // Helper methods
    private String generateQRCodeUrl(String token) {
        // In production, you would generate actual QR code
        // For now, return a placeholder that can be used by frontend
        return "https://api.qr-server.com/v1/create-qr-code/?size=200x200&data=" + 
               generateInviteUrl(token);
    }

    private String generateInviteUrl(String token) {
        return "https://matrix-ads-frontend.onrender.com/invite-signup?token=" + token;
    }
}