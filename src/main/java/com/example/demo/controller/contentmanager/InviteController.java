package com.example.demo.controller.contentmanager;

import com.example.demo.dao.InviteDao;
import com.example.demo.dao.UserDao;
import com.example.demo.dao.GroupDao;
import com.example.demo.model.Invite;
import com.example.demo.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/content-manager/invites")
public class InviteController {

    @Autowired
    private InviteDao inviteDao;

    @Autowired
    private UserDao userDao;

    // Generate a new invite
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateInvite(@RequestBody Map<String, Object> requestBody) {
        try {
            String managerId = (String) requestBody.get("managerId");
            String groupName = (String) requestBody.get("groupName");
            Integer expiresInDays = (Integer) requestBody.get("expiresInDays");

            // Validate required fields
            if (managerId == null || groupName == null || groupName.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Missing required fields: managerId and groupName");
                return ResponseEntity.badRequest().body(response);
            }

            // Verify manager exists and has correct role
            User manager = userDao.findById(managerId);
            if (manager == null || !"content_manager".equals(manager.getRole())) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Invalid manager or insufficient permissions");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            // Check if there's already a pending invite for this group name
            List<Invite> existingInvites = inviteDao.findByManagerId(managerId);
            for (Invite existing : existingInvites) {
                if (groupName.equals(existing.getGroupName()) && 
                    "pending".equals(existing.getStatus()) && !existing.isExpired()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Active group invite already exists for this group name");
                    return ResponseEntity.badRequest().body(response);
                }
            }

            // Generate unique token
            String token = UUID.randomUUID().toString() + "-" + System.currentTimeMillis();

            // Calculate expiration date
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, expiresInDays != null ? expiresInDays : 7);
            Date expiresAt = calendar.getTime();

            // Create and save group invite
            Invite invite = new Invite(
                null, // ID will be auto-generated
                managerId,
                manager.getUsername(),
                groupName,
                null, // groupId will be set when first user joins
                "content_creator", // Always content_creator for now
                token,
                "pending",
                new Date(),
                expiresAt
            );

            inviteDao.save(invite);

            // Generate QR code data for WeChat Mini Program
            Map<String, Object> qrData = new HashMap<>();
            qrData.put("type", "invite");
            qrData.put("token", invite.getToken());
            qrData.put("groupName", invite.getGroupName());
            qrData.put("managerName", invite.getManagerName());
            qrData.put("platform", "miniprogram");
            qrData.put("expiresAt", invite.getExpiresAt().getTime());

            // Generate mini program QR code URL
            String miniProgramQRUrl = generateMiniProgramQRUrl(invite.getToken());
            
            // Generate web fallback URL
            String webUrl = generateWebInviteUrl(invite.getToken());

            // Return success response with invite data
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Group invite generated successfully");
            response.put("id", invite.getId());
            response.put("token", invite.getToken());
            response.put("managerId", invite.getManagerId());
            response.put("groupName", invite.getGroupName());
            response.put("managerName", invite.getManagerName());
            response.put("expiresAt", invite.getExpiresAt());
            response.put("createdAt", invite.getCreatedAt());
            response.put("status", invite.getStatus());
            response.put("qrData", qrData);
            response.put("miniProgramQRUrl", miniProgramQRUrl);
            response.put("webUrl", webUrl);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to generate invite: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Get all invites for a manager
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Map<String, Object>>> getInvitesByManager(@PathVariable String userId) {
        try {
            List<Invite> invites = inviteDao.findByManagerId(userId);
            List<Map<String, Object>> inviteList = new ArrayList<>();

            for (Invite invite : invites) {
                Map<String, Object> inviteData = new HashMap<>();
                inviteData.put("id", invite.getId());
                inviteData.put("groupName", invite.getGroupName());
                inviteData.put("token", invite.getToken());
                inviteData.put("status", invite.getStatus());
                inviteData.put("createdAt", invite.getCreatedAt());
                inviteData.put("expiresAt", invite.getExpiresAt());
                inviteData.put("acceptedAt", invite.getAcceptedAt());
                inviteList.add(inviteData);
            }

            return ResponseEntity.ok(inviteList);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ArrayList<>());
        }
    }

    // Delete an invite
    @DeleteMapping("/{inviteId}")
    public ResponseEntity<Map<String, Object>> deleteInvite(@PathVariable String inviteId) {
        try {
            Invite invite = inviteDao.findById(inviteId);
            if (invite == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Invite not found");
                return ResponseEntity.notFound().build();
            }

            inviteDao.delete(inviteId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Invite deleted successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to delete invite: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Helper method to generate WeChat Mini Program QR URL
    private String generateMiniProgramQRUrl(String token) {
        // WeChat Mini Program QR code format
        // This should be replaced with your actual mini program AppID and path
        String appId = "your-miniprogram-appid"; // Replace with actual AppID
        String path = "pages/invite/invite"; // Mini program page for invite registration
        String scene = "token=" + token; // Parameters passed to mini program
        
        // In production, you would call WeChat API to generate actual QR code
        // For now, return a format that can be used to generate QR code on frontend
        return String.format("https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=ACCESS_TOKEN&scene=%s&page=%s", 
                            scene, path);
    }

    // Helper method to generate web fallback URL
    private String generateWebInviteUrl(String token) {
        // This should use your actual domain
        String baseUrl = "https://matrix-ads-frontend.onrender.com"; // Replace with actual domain
        return baseUrl + "/invite-signup?token=" + token;
    }

}