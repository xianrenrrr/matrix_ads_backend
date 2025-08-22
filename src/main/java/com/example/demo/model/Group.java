package com.example.demo.model;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Represents a group of content creators managed by a content manager.
 * Groups are permanent and allow managers to organize and track their creators.
 */
public class Group {
    private String id;                               // Unique group ID
    private String managerId;                        // ID of the content manager who owns this group
    private String managerName;                      // Name of the content manager
    private String groupName;                        // Name of the group
    private String description;                      // Group description
    private String token;                            // Unique token for joining the group
    private String status;                           // "active" or "inactive"
    private Date createdAt;                          // Group creation timestamp
    private Date updatedAt;                          // Last update timestamp
    
    // Member Management
    private List<String> memberIds;                 // List of content creator user IDs
    private int memberCount;                         // Cached count of members
    
    // AI Settings
    private double aiApprovalThreshold = 0.85;      // Similarity threshold for auto-approval (0-1)
    private boolean aiAutoApprovalEnabled = true;   // Whether AI auto-approval is enabled
    private boolean allowManualOverride = true;     // Whether managers can override AI decisions
    private Map<String, Double> sceneThresholds;    // Custom thresholds for specific scenes
    
    // Template Management
    private List<String> assignedTemplates;         // Templates assigned to this group

    public Group() {
        this.memberIds = new ArrayList<>();
        this.sceneThresholds = new HashMap<>();
        this.assignedTemplates = new ArrayList<>();
        this.status = "active";
        this.createdAt = new Date();
        this.updatedAt = new Date();
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

    // Standard getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }

    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { 
        this.description = description;
        this.updatedAt = new Date();
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    
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
    
    public List<String> getAssignedTemplates() {
        if (assignedTemplates == null) {
            assignedTemplates = new ArrayList<>();
        }
        return assignedTemplates;
    }
    
    public void setAssignedTemplates(List<String> assignedTemplates) {
        this.assignedTemplates = assignedTemplates;
        this.updatedAt = new Date();
    }
}