package com.example.demo.model;

import java.util.Date;
import java.util.List;

public class Group {
    private String id;
    private String groupName;         // Name of the group (e.g., "Shanghai Store Team")
    private String managerId;         // ID of the content manager who created the group
    private String managerName;       // Name of the content manager
    private List<String> memberIds;   // List of user IDs who are members of this group
    private String description;       // Optional description of the group
    private Date createdAt;          // When the group was created
    private Date updatedAt;          // When the group was last updated
    private boolean active;          // Whether the group is active

    public Group() {}

    public Group(String id, String groupName, String managerId, String managerName, 
                 List<String> memberIds, String description, Date createdAt, Date updatedAt, boolean active) {
        this.id = id;
        this.groupName = groupName;
        this.managerId = managerId;
        this.managerName = managerName;
        this.memberIds = memberIds;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.active = active;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }

    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }

    public List<String> getMemberIds() { return memberIds; }
    public void setMemberIds(List<String> memberIds) { this.memberIds = memberIds; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    // Helper methods
    public boolean isMember(String userId) {
        return memberIds != null && memberIds.contains(userId);
    }

    public void addMember(String userId) {
        if (memberIds != null && !memberIds.contains(userId)) {
            memberIds.add(userId);
            this.updatedAt = new Date();
        }
    }

    public void removeMember(String userId) {
        if (memberIds != null && memberIds.contains(userId)) {
            memberIds.remove(userId);
            this.updatedAt = new Date();
        }
    }
}