package com.example.demo.model;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Represents a time-limited assignment of a template to a group.
 * When a manager pushes a template to a group, a snapshot is created
 * with an expiration date. After expiration, the assignment is auto-deleted.
 */
public class TemplateAssignment {
    private String id;
    private String masterTemplateId;
    private String groupId;
    private ManualTemplate templateSnapshot;  // Full copy of template at push time
    
    // Time management
    private Date pushedAt;
    private Date expiresAt;  // null = permanent
    private String status;  // "active", "expiring_soon", "expired"
    private Integer durationDays;
    
    // Metadata
    private String pushedBy;
    private Date lastRenewed;
    
    public TemplateAssignment() {
        this.pushedAt = new Date();
        this.status = "active";
    }
    
    // Helper methods
    public boolean isExpired() {
        return expiresAt != null && new Date().after(expiresAt);
    }
    
    public boolean isExpiringSoon() {
        if (expiresAt == null) return false;
        long daysUntilExpiry = getDaysUntilExpiry();
        return daysUntilExpiry <= 7 && daysUntilExpiry > 0;
    }
    
    public long getDaysUntilExpiry() {
        if (expiresAt == null) return -1;
        long diffInMillies = expiresAt.getTime() - new Date().getTime();
        return TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
    }
    
    public void updateStatus() {
        if (isExpired()) {
            this.status = "expired";
        } else if (isExpiringSoon()) {
            this.status = "expiring_soon";
        } else {
            this.status = "active";
        }
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getMasterTemplateId() {
        return masterTemplateId;
    }
    
    public void setMasterTemplateId(String masterTemplateId) {
        this.masterTemplateId = masterTemplateId;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public ManualTemplate getTemplateSnapshot() {
        return templateSnapshot;
    }
    
    public void setTemplateSnapshot(ManualTemplate templateSnapshot) {
        this.templateSnapshot = templateSnapshot;
    }
    
    public Date getPushedAt() {
        return pushedAt;
    }
    
    public void setPushedAt(Date pushedAt) {
        this.pushedAt = pushedAt;
    }
    
    public Date getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Integer getDurationDays() {
        return durationDays;
    }
    
    public void setDurationDays(Integer durationDays) {
        this.durationDays = durationDays;
    }
    
    public String getPushedBy() {
        return pushedBy;
    }
    
    public void setPushedBy(String pushedBy) {
        this.pushedBy = pushedBy;
    }
    
    public Date getLastRenewed() {
        return lastRenewed;
    }
    
    public void setLastRenewed(Date lastRenewed) {
        this.lastRenewed = lastRenewed;
    }
}
