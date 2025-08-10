package com.example.demo.controller.contentmanager;

import com.example.demo.dao.InviteDao;
import com.example.demo.dao.UserDao;
import com.example.demo.model.Invite;
import com.example.demo.model.User;
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

    // 1. Create Group/Invite
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createGroup(@RequestBody Map<String, Object> requestBody) {
        try {
            String managerId = (String) requestBody.get("managerId");
            String groupName = (String) requestBody.get("groupName");
            String description = (String) requestBody.get("description");

            // Validate required fields
            if (managerId == null || groupName == null || groupName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Missing required fields: managerId and groupName"
                ));
            }

            // Verify manager exists and has correct role
            User manager = userDao.findById(managerId);
            if (manager == null || !"content_manager".equals(manager.getRole())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "Invalid manager or insufficient permissions"
                ));
            }

            // Check if group name already exists for this manager
            List<Invite> existingGroups = inviteDao.findByManagerId(managerId);
            for (Invite existing : existingGroups) {
                if (groupName.equals(existing.getGroupName()) && "active".equals(existing.getStatus())) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Group with this name already exists"
                    ));
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

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Group created successfully");
            response.put("id", group.getId());
            response.put("groupName", group.getGroupName());
            response.put("description", group.getDescription());
            response.put("managerId", group.getManagerId());
            response.put("token", group.getToken());
            response.put("qrCodeUrl", qrCodeUrl);
            response.put("inviteUrl", inviteUrl);
            response.put("memberCount", group.getMemberCount());
            response.put("aiApprovalThreshold", group.getAiApprovalThreshold());
            response.put("aiAutoApprovalEnabled", group.isAiAutoApprovalEnabled());
            response.put("createdAt", group.getCreatedAt());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Failed to create group: " + e.getMessage()
            ));
        }
    }

    // 2. List Manager's Groups
    @GetMapping("/manager/{managerId}")
    public ResponseEntity<List<Map<String, Object>>> getGroupsByManager(@PathVariable String managerId) {
        try {
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

            return ResponseEntity.ok(groupSummaries);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ArrayList<>());
        }
    }

    // 3. Get Group QR Code
    @GetMapping("/{groupId}/qrcode")
    public ResponseEntity<Map<String, Object>> getGroupQRCode(@PathVariable String groupId) {
        try {
            Invite group = inviteDao.findById(groupId);
            if (group == null || !"active".equals(group.getStatus())) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("groupId", group.getId());
            response.put("groupName", group.getGroupName());
            response.put("token", group.getToken());
            response.put("qrCodeUrl", generateQRCodeUrl(group.getToken()));
            response.put("inviteUrl", generateInviteUrl(group.getToken()));
            response.put("miniProgramPath", "pages/invite/invite?token=" + group.getToken());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Failed to get QR code: " + e.getMessage()
            ));
        }
    }

    // 4. Delete Group
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Map<String, Object>> deleteGroup(@PathVariable String groupId) {
        try {
            Invite group = inviteDao.findById(groupId);
            if (group == null) {
                return ResponseEntity.notFound().build();
            }

            inviteDao.delete(groupId);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Group deleted successfully"
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Failed to delete group: " + e.getMessage()
            ));
        }
    }

    // 5. Add Member to Group
    @PostMapping("/{groupId}/members")
    public ResponseEntity<Map<String, Object>> addMember(
            @PathVariable String groupId,
            @RequestBody Map<String, Object> requestBody) {
        try {
            String userId = (String) requestBody.get("userId");
            String userEmail = (String) requestBody.get("userEmail");

            if (userId == null && userEmail == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Either userId or userEmail is required"
                ));
            }

            Invite group = inviteDao.findById(groupId);
            if (group == null || !"active".equals(group.getStatus())) {
                return ResponseEntity.notFound().build();
            }

            // Find user by ID or email
            User user = null;
            if (userId != null) {
                user = userDao.findById(userId);
            } else {
                user = userDao.findByEmail(userEmail);
            }

            if (user == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "User not found"
                ));
            }

            // Add member to group
            group.addMember(user.getId());
            inviteDao.update(group);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Member added successfully",
                "memberCount", group.getMemberCount()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Failed to add member: " + e.getMessage()
            ));
        }
    }

    // 6. Remove Member from Group
    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<Map<String, Object>> removeMember(
            @PathVariable String groupId,
            @PathVariable String userId) {
        try {
            Invite group = inviteDao.findById(groupId);
            if (group == null || !"active".equals(group.getStatus())) {
                return ResponseEntity.notFound().build();
            }

            group.removeMember(userId);
            inviteDao.update(group);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Member removed successfully",
                "memberCount", group.getMemberCount()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Failed to remove member: " + e.getMessage()
            ));
        }
    }

    // 7. Get Group Members
    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<Map<String, Object>>> getGroupMembers(@PathVariable String groupId) {
        try {
            Invite group = inviteDao.findById(groupId);
            if (group == null || !"active".equals(group.getStatus())) {
                return ResponseEntity.notFound().build();
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

            return ResponseEntity.ok(members);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ArrayList<>());
        }
    }

    // 8. Update Group AI Settings
    @PutMapping("/{groupId}/ai-settings")
    public ResponseEntity<Map<String, Object>> updateGroupAISettings(
            @PathVariable String groupId,
            @RequestBody Map<String, Object> aiSettings) {
        try {
            Invite group = inviteDao.findById(groupId);
            if (group == null || !"active".equals(group.getStatus())) {
                return ResponseEntity.notFound().build();
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

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "AI settings updated successfully",
                "aiApprovalThreshold", group.getAiApprovalThreshold(),
                "aiAutoApprovalEnabled", group.isAiAutoApprovalEnabled(),
                "allowManualOverride", group.isAllowManualOverride()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Failed to update AI settings: " + e.getMessage()
            ));
        }
    }

    // 9. Get Group AI Settings
    @GetMapping("/{groupId}/ai-settings")
    public ResponseEntity<Map<String, Object>> getGroupAISettings(@PathVariable String groupId) {
        try {
            Invite group = inviteDao.findById(groupId);
            if (group == null || !"active".equals(group.getStatus())) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "groupId", group.getId(),
                "groupName", group.getGroupName(),
                "aiSettings", Map.of(
                    "aiApprovalThreshold", group.getAiApprovalThreshold(),
                    "aiAutoApprovalEnabled", group.isAiAutoApprovalEnabled(),
                    "allowManualOverride", group.isAllowManualOverride(),
                    "sceneThresholds", group.getSceneThresholds()
                )
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Failed to get AI settings: " + e.getMessage()
            ));
        }
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