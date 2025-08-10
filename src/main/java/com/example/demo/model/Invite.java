package com.example.demo.model;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class Invite {
    private String id;
    private String managerId;      // ID of the content manager who sent the invite
    private String managerName;    // Name of the content manager
    private String groupName;      // Name of the group being invited to join
    private String groupId;        // ID of the group (will be created when first user joins)
    private String role;           // Role to assign (always "content_creator" for now)
    private String token;          // Unique token for the invite link
    private String status;         // "active", "inactive" (groups are permanent)
    private Date createdAt;        // When the group was created
    private Date expiresAt;        // Optional - When the invite expires (null for permanent groups)
    private Date acceptedAt;       // When first user joined (null if no members)
    private Date updatedAt;        // When the group was last updated
    private String description;    // Optional description of the group
    
    // Member Management
    private List<String> memberIds;   // List of user IDs who are members of this group
    private int memberCount;          // Cached count of members
    
    // AI Settings for this group
    private double aiApprovalThreshold = 0.85;        // Similarity threshold for auto-approval (0-1)
    private boolean aiAutoApprovalEnabled = true;     // Whether AI auto-approval is enabled
    private boolean allowManualOverride = true;       // Whether managers can override AI decisions
    private Map<String, Double> sceneThresholds;      // Custom thresholds for specific scenes

    public Invite() {}

    // Constructor for group invites
    public Invite(String id, String managerId, String managerName, String groupName, 
                  String groupId, String role, String token, String status, 
                  Date createdAt, Date expiresAt) {
        this.id = id;
        this.managerId = managerId;
        this.managerName = managerName;
        this.groupName = groupName;
        this.groupId = groupId;
        this.role = role;
        this.token = token;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.acceptedAt = null;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }

    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }


    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }

    public Date getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Date acceptedAt) { this.acceptedAt = acceptedAt; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { 
        this.description = description;
        this.updatedAt = new Date();
    }
    
    // Member Management
    public List<String> getMemberIds() { 
        if (memberIds == null) {
            memberIds = new ArrayList<>();
        }
        return memberIds; 
    }
    public void setMemberIds(List<String> memberIds) { 
        this.memberIds = memberIds;
        this.memberCount = memberIds != null ? memberIds.size() : 0;
        this.updatedAt = new Date();
    }
    
    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
    
    // AI Settings
    public double getAiApprovalThreshold() { return aiApprovalThreshold; }
    public void setAiApprovalThreshold(double aiApprovalThreshold) { 
        this.aiApprovalThreshold = aiApprovalThreshold; 
        this.updatedAt = new Date();
    }
    
    public boolean isAiAutoApprovalEnabled() { return aiAutoApprovalEnabled; }
    public void setAiAutoApprovalEnabled(boolean aiAutoApprovalEnabled) { 
        this.aiAutoApprovalEnabled = aiAutoApprovalEnabled;
        this.updatedAt = new Date();
    }
    
    public boolean isAllowManualOverride() { return allowManualOverride; }
    public void setAllowManualOverride(boolean allowManualOverride) { 
        this.allowManualOverride = allowManualOverride;
        this.updatedAt = new Date();
    }
    
    public Map<String, Double> getSceneThresholds() { 
        if (sceneThresholds == null) {
            sceneThresholds = new HashMap<>();
        }
        return sceneThresholds; 
    }
    public void setSceneThresholds(Map<String, Double> sceneThresholds) { 
        this.sceneThresholds = sceneThresholds;
        this.updatedAt = new Date();
    }

    // Helper methods
    public boolean isExpired() {
        // Groups never expire if expiresAt is null
        return expiresAt != null && new Date().after(expiresAt);
    }

    public boolean isValid() {
        // Groups are valid if active and not expired
        return "active".equals(status) && !isExpired();
    }
    
    // Member management helper methods
    public boolean isMember(String userId) {
        return getMemberIds().contains(userId);
    }
    
    public void addMember(String userId) {
        if (!getMemberIds().contains(userId)) {
            getMemberIds().add(userId);
            this.memberCount = getMemberIds().size();
            this.updatedAt = new Date();
            
            // Set acceptedAt to first join time
            if (this.acceptedAt == null) {
                this.acceptedAt = new Date();
            }
        }
    }
    
    public void removeMember(String userId) {
        if (getMemberIds().remove(userId)) {
            this.memberCount = getMemberIds().size();
            this.updatedAt = new Date();
        }
    }
    
    // Get the threshold for a specific scene (falls back to global threshold)
    public double getThresholdForScene(String sceneId) {
        return getSceneThresholds().getOrDefault(sceneId, aiApprovalThreshold);
    }
    
    // Set scene-specific threshold
    public void setSceneThreshold(String sceneId, double threshold) {
        getSceneThresholds().put(sceneId, threshold);
        this.updatedAt = new Date();
    }
}