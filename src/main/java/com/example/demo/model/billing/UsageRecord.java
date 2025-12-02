package com.example.demo.model.billing;

import java.util.Date;

/**
 * UsageRecord model for tracking manager usage metrics per billing period
 */
public class UsageRecord {
    private String id;
    private String managerId;
    private String subscriptionId;
    private Date periodStart;
    private Date periodEnd;
    private UsageMetrics metrics;
    private Date createdAt;
    
    public UsageRecord() {
        this.createdAt = new Date();
        this.metrics = new UsageMetrics();
    }
    
    public UsageRecord(String managerId, String subscriptionId, Date periodStart, Date periodEnd) {
        this();
        this.managerId = managerId;
        this.subscriptionId = subscriptionId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }
    
    public String getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
    
    public Date getPeriodStart() { return periodStart; }
    public void setPeriodStart(Date periodStart) { this.periodStart = periodStart; }
    
    public Date getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Date periodEnd) { this.periodEnd = periodEnd; }
    
    public UsageMetrics getMetrics() { return metrics; }
    public void setMetrics(UsageMetrics metrics) { this.metrics = metrics; }
    
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    
    /**
     * Inner class for usage metrics
     */
    public static class UsageMetrics {
        private int activeCreators;      // Creators who submitted in period
        private int totalCreators;       // All creators linked to manager
        private int activeTemplates;     // Templates with submissions
        private int totalTemplates;      // All templates owned
        private int totalGroups;         // Groups owned
        private int videoSubmissions;    // Total scene submissions
        private int aiAnalysisCount;     // AI comparisons performed
        private double storageUsedGB;    // Storage used in GB
        
        public UsageMetrics() {}
        
        // Getters and Setters
        public int getActiveCreators() { return activeCreators; }
        public void setActiveCreators(int activeCreators) { this.activeCreators = activeCreators; }
        
        public int getTotalCreators() { return totalCreators; }
        public void setTotalCreators(int totalCreators) { this.totalCreators = totalCreators; }
        
        public int getActiveTemplates() { return activeTemplates; }
        public void setActiveTemplates(int activeTemplates) { this.activeTemplates = activeTemplates; }
        
        public int getTotalTemplates() { return totalTemplates; }
        public void setTotalTemplates(int totalTemplates) { this.totalTemplates = totalTemplates; }
        
        public int getTotalGroups() { return totalGroups; }
        public void setTotalGroups(int totalGroups) { this.totalGroups = totalGroups; }
        
        public int getVideoSubmissions() { return videoSubmissions; }
        public void setVideoSubmissions(int videoSubmissions) { this.videoSubmissions = videoSubmissions; }
        
        public int getAiAnalysisCount() { return aiAnalysisCount; }
        public void setAiAnalysisCount(int aiAnalysisCount) { this.aiAnalysisCount = aiAnalysisCount; }
        
        public double getStorageUsedGB() { return storageUsedGB; }
        public void setStorageUsedGB(double storageUsedGB) { this.storageUsedGB = storageUsedGB; }
        
        // Increment methods for real-time tracking
        public void incrementActiveCreators() { this.activeCreators++; }
        public void incrementVideoSubmissions() { this.videoSubmissions++; }
        public void incrementAiAnalysisCount() { this.aiAnalysisCount++; }
        public void addStorageUsed(double gb) { this.storageUsedGB += gb; }
    }
}
