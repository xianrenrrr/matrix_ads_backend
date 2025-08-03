package com.example.demo.model;

import java.util.Date;

public class Invite {
    private String id;
    private String managerId;      // ID of the content manager who sent the invite
    private String managerName;    // Name of the content manager
    private String inviteeEmail;   // Optional email - can be null for WeChat-only invites
    private String inviteeName;    // Name of the person being invited
    private String role;           // Role to assign (always "content_creator" for now)
    private String token;          // Unique token for the invite link
    private String status;         // "pending", "accepted", "expired", "cancelled"
    private Date createdAt;        // When the invite was created
    private Date expiresAt;        // When the invite expires
    private Date acceptedAt;       // When the invite was accepted (null if not accepted)

    public Invite() {}

    public Invite(String id, String managerId, String managerName, String inviteeEmail, 
                  String inviteeName, String role, String token, String status, 
                  Date createdAt, Date expiresAt) {
        this.id = id;
        this.managerId = managerId;
        this.managerName = managerName;
        this.inviteeEmail = inviteeEmail;
        this.inviteeName = inviteeName;
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

    public String getInviteeEmail() { return inviteeEmail; }
    public void setInviteeEmail(String inviteeEmail) { this.inviteeEmail = inviteeEmail; }

    public String getInviteeName() { return inviteeName; }
    public void setInviteeName(String inviteeName) { this.inviteeName = inviteeName; }

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

    // Helper methods
    public boolean isExpired() {
        return new Date().after(expiresAt);
    }

    public boolean isValid() {
        return "pending".equals(status) && !isExpired();
    }
}