package com.example.demo.controller.contentmanager;

import com.example.demo.dao.GroupDao;
import com.example.demo.dao.UserDao;
import com.example.demo.dao.TemplateDao;
import com.example.demo.model.Group;
import com.example.demo.model.User;
import com.example.demo.model.ManualTemplate;
import com.example.demo.api.ApiResponse;
import com.example.demo.service.I18nService;
import com.example.demo.service.WeChatMiniProgramService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/content-manager/groups")
@CrossOrigin(origins = {"http://localhost:4040", "https://matrix-ads-frontend.onrender.com"})
public class GroupController {

    @Autowired
    private GroupDao groupDao; // Using GroupDao for group management

    @Autowired
    private UserDao userDao;
    
    @Autowired
    private TemplateDao templateDao;
    
    @Autowired
    private I18nService i18nService;
    
    @Autowired
    private WeChatMiniProgramService weChatService;

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
        List<Group> existingGroups = groupDao.findByManagerId(managerId);
        boolean groupExists = existingGroups.stream()
            .anyMatch(g -> groupName.equals(g.getGroupName()) && "active".equals(g.getStatus()));
        if (groupExists) {
            throw new IllegalArgumentException("Group with this name already exists");
        }

        // Generate unique token for joining
        String token = "group_" + UUID.randomUUID().toString().replace("-", "");

        // Create permanent group
        Group group = new Group();
        group.setManagerId(managerId);
        group.setManagerName(manager.getUsername() != null ? manager.getUsername() : manager.getEmail());
        group.setGroupName(groupName);
        group.setDescription(description);
        group.setToken(token);
        group.setStatus("active");
        group.setCreatedAt(new Date());
        group.setUpdatedAt(new Date());
        group.setMemberIds(new ArrayList<>());
        group.setMemberCount(0);
        
        // Set default AI settings
        group.setAiApprovalThreshold(0.70);
        group.setAiAutoApprovalEnabled(true);
        group.setAllowManualOverride(true);

        groupDao.save(group);

        // Generate QR code and invite URLs
        String qrCodeUrl = generateQRCodeUrl(group.getId());
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
        List<Group> groups = groupDao.findByManagerId(managerId);
        List<Map<String, Object>> groupSummaries = new ArrayList<>();

        for (Group group : groups) {
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

    // 3. Get Group Info by ID
    @GetMapping("/{groupId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGroupById(@PathVariable String groupId,
                                                                         @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        System.out.println("🔍🔍🔍 [GroupController] API CALL RECEIVED - getGroupById");
        System.out.println("🔍 [GroupController] groupId parameter: '" + groupId + "'");
        System.out.println("🔍 [GroupController] Accept-Language: " + acceptLanguage);
        System.out.println("🔍 [GroupController] Request timestamp: " + new java.util.Date());
        
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        Group group = groupDao.findById(groupId);
        
        System.out.println("🔍 [GroupController] Group found: " + (group != null));
        if (group != null) {
            System.out.println("🔍 [GroupController] Group status: " + group.getStatus());
            System.out.println("🔍 [GroupController] Group name: " + group.getGroupName());
        }
        if (group == null || !"active".equals(group.getStatus())) {
            throw new NoSuchElementException("Group not found with ID: " + groupId);
        }

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("id", group.getId());
        responseData.put("groupName", group.getGroupName());
        responseData.put("managerName", group.getManagerName());
        responseData.put("description", group.getDescription());
        responseData.put("token", group.getToken());
        responseData.put("memberCount", group.getMemberCount());
        responseData.put("aiApprovalThreshold", group.getAiApprovalThreshold());
        responseData.put("aiAutoApprovalEnabled", group.isAiAutoApprovalEnabled());
        responseData.put("createdAt", group.getCreatedAt());

        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }

    // 4. Get Group QR Code
    @GetMapping("/{groupId}/qrcode")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGroupQRCode(@PathVariable String groupId,
                                                                            @RequestParam(value = "regenerate", defaultValue = "false") boolean regenerate,
                                                                            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        Group group = groupDao.findById(groupId);
        if (group == null || !"active".equals(group.getStatus())) {
            throw new NoSuchElementException("Group not found with ID: " + groupId);
        }

        // Check if we have a cached QR code and don't need to regenerate
        String qrCodeUrl;
        if (!regenerate && group.getQrCodeUrl() != null && !group.getQrCodeUrl().isEmpty()) {
            qrCodeUrl = group.getQrCodeUrl();
            System.out.println("✅ Using cached QR code for group: " + groupId);
        } else {
            // Generate new QR code
            qrCodeUrl = generateQRCodeUrl(group.getId());
            
            // Save QR code to database
            group.setQrCodeUrl(qrCodeUrl);
            groupDao.update(group);
            System.out.println("✅ Generated and cached new QR code for group: " + groupId);
        }

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("groupId", group.getId());
        responseData.put("groupName", group.getGroupName());
        responseData.put("token", group.getToken());
        responseData.put("qrCodeUrl", qrCodeUrl);
        responseData.put("qrCodeCached", !regenerate && group.getQrCodeUrl() != null);
        responseData.put("qrCodeGeneratedAt", group.getQrCodeGeneratedAt());
        responseData.put("inviteUrl", generateInviteUrl(group.getToken()));
        responseData.put("miniProgramPath", "pages/signup/signup?token=" + group.getToken());

        String message = i18nService.getMessage("qr.generated", language);
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }

    // 4. Delete Group
    @DeleteMapping("/{groupId}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable String groupId,
                                                         @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        Group group = groupDao.findById(groupId);
        if (group == null) {
            throw new NoSuchElementException("Group not found with ID: " + groupId);
        }

        groupDao.delete(groupId);

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

        Group group = groupDao.findById(groupId);
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
        groupDao.update(group);

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
        Group group = groupDao.findById(groupId);
        if (group == null || !"active".equals(group.getStatus())) {
            throw new NoSuchElementException("Group not found with ID: " + groupId);
        }

        group.removeMember(userId);
        groupDao.update(group);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("memberCount", group.getMemberCount());
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }
    
    // 6b. Remove Multiple Members from Group
    @DeleteMapping("/{groupId}/members")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeMultipleMembers(
            @PathVariable String groupId,
            @RequestBody Map<String, Object> requestBody,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        @SuppressWarnings("unchecked")
        List<String> userIds = (List<String>) requestBody.get("userIds");
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("userIds list is required");
        }
        
        Group group = groupDao.findById(groupId);
        if (group == null || !"active".equals(group.getStatus())) {
            throw new NoSuchElementException("Group not found with ID: " + groupId);
        }

        // Remove all specified members
        int removedCount = 0;
        for (String userId : userIds) {
            if (group.getMemberIds().contains(userId)) {
                group.removeMember(userId);
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            groupDao.update(group);
        }

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("memberCount", group.getMemberCount());
        responseData.put("removedCount", removedCount);
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }

    // 7. Get Group Members
    @GetMapping("/{groupId}/members")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getGroupMembers(@PathVariable String groupId,
                                                                                   @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        Group group = groupDao.findById(groupId);
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
                memberInfo.put("joinedAt", group.getCreatedAt());
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
        Group group = groupDao.findById(groupId);
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

        groupDao.update(group);

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
        Group group = groupDao.findById(groupId);
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

    private String generateQRCodeUrl(String token) {
        try {
            // Force homepage = true → omit "page" and set check_path=false
            // (safer until your mini program’s page is actually deployed)
            // Pass just the group ID - WeChatMiniProgramService will add "g=" prefix
            System.out.println("🔍 [GroupController] Generating QR code with groupId: " + token);
            return weChatService.generateMiniProgramQRCode(token, true);
        } catch (Exception e) {
            // Log and fallback
            System.err.println("❌ Failed to generate WeChat QR code, using fallback: " + e.getMessage());
            String miniProgramPath = "pages/signup/signup?token=" + token;
            try {
                String encodedPath = URLEncoder.encode(miniProgramPath, StandardCharsets.UTF_8);
                return "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + encodedPath;
            } catch (Exception ex) {
                return "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + miniProgramPath;
            }
        }
    }

    private String generateInviteUrl(String token) {
        return "https://matrix-ads-frontend.onrender.com/invite-signup?token=" + token;
    }
    
    /**
     * Get active template assignments for a group (for mini program)
     * GET /content-manager/groups/{groupId}/templates
     */
    @GetMapping("/{groupId}/templates")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getGroupTemplates(
            @PathVariable String groupId,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        // Get active assignments for this group
        List<com.example.demo.model.TemplateAssignment> assignments = 
            templateAssignmentDao.getAssignmentsByGroup(groupId);
        
        List<Map<String, Object>> templates = new ArrayList<>();
        for (com.example.demo.model.TemplateAssignment assignment : assignments) {
            // Skip expired assignments
            if (assignment.isExpired()) {
                continue;
            }
            
            ManualTemplate snapshot = assignment.getTemplateSnapshot();
            
            Map<String, Object> templateData = new HashMap<>();
            // Use assignment ID as template ID for mini program
            templateData.put("id", assignment.getId());
            templateData.put("masterTemplateId", assignment.getMasterTemplateId());
            
            // Handle null snapshot gracefully - EXACT SAME LOGIC AS UserController
            if (snapshot != null) {
                templateData.put("templateTitle", snapshot.getTemplateTitle());
                templateData.put("templateDescription", snapshot.getTemplateDescription());
                templateData.put("videoPurpose", snapshot.getVideoPurpose());
                templateData.put("tone", snapshot.getTone());
                templateData.put("totalVideoLength", snapshot.getTotalVideoLength());
                templateData.put("videoFormat", snapshot.getVideoFormat());
                
                // Convert thumbnail URL to proxy URL for mini app
                String thumbnailUrl = snapshot.getThumbnailUrl();
                if (thumbnailUrl != null) {
                    thumbnailUrl = convertToProxyUrl(thumbnailUrl);
                }
                templateData.put("thumbnailUrl", thumbnailUrl);
                templateData.put("sceneCount", snapshot.getScenes() != null ? snapshot.getScenes().size() : 0);
            } else {
                // Fallback values if snapshot is null
                templateData.put("templateTitle", "Template " + assignment.getMasterTemplateId());
                templateData.put("templateDescription", null);
                templateData.put("videoPurpose", null);
                templateData.put("tone", null);
                templateData.put("totalVideoLength", 0);
                templateData.put("videoFormat", null);
                templateData.put("thumbnailUrl", null);
                templateData.put("sceneCount", 0);
            }
            
            // Add assignment metadata
            templateData.put("pushedAt", assignment.getPushedAt());
            templateData.put("expiresAt", assignment.getExpiresAt());
            templateData.put("daysUntilExpiry", assignment.getDaysUntilExpiry());
            
            templates.add(templateData);
        }
        
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, templates));
    }
    
    @Autowired
    private com.example.demo.dao.TemplateAssignmentDao templateAssignmentDao;
    
    /**
     * Convert Google Storage URL to proxy URL for mini app
     * Example: https://storage.googleapis.com/matrix_ads_video/keyframes/abc.jpg?signed -> /images/proxy?path=keyframes/abc.jpg
     */
    private String convertToProxyUrl(String storageUrl) {
        if (storageUrl == null || storageUrl.isEmpty()) {
            return storageUrl;
        }
        
        try {
            // Remove query params for signed URLs
            String urlWithoutQuery = storageUrl.split("\\?")[0];
            String[] urlParts = urlWithoutQuery.split("/");
            
            // Find bucket name index
            int bucketIndex = -1;
            for (int i = 0; i < urlParts.length; i++) {
                if ("matrix_ads_video".equals(urlParts[i])) {
                    bucketIndex = i;
                    break;
                }
            }
            
            if (bucketIndex == -1) {
                return storageUrl; // Return original if bucket not found
            }
            
            // Extract path after bucket name
            StringBuilder pathBuilder = new StringBuilder();
            for (int i = bucketIndex + 1; i < urlParts.length; i++) {
                if (pathBuilder.length() > 0) {
                    pathBuilder.append("/");
                }
                pathBuilder.append(urlParts[i]);
            }
            
            String path = pathBuilder.toString();
            return "/images/proxy?path=" + java.net.URLEncoder.encode(path, "UTF-8");
            
        } catch (Exception e) {
            // Return original URL if conversion fails
            return storageUrl;
        }
    }
}