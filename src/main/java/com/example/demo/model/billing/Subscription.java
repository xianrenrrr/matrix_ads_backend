package com.example.demo.model.billing;

import java.util.Date;

/**
 * Subscription model for tracking manager billing subscriptions
 */
public class Subscription {
    private String id;
    private String managerId;
    private String tier;  // FREE, STARTER, PRO, ENTERPRISE
    private String status;  // active, cancelled, past_due, trialing
    private Date currentPeriodStart;
    private Date currentPeriodEnd;
    private Date trialEnd;
    private boolean cancelAtPeriodEnd;
    private Date createdAt;
    private Date updatedAt;
    
    // Status constants
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_PAST_DUE = "past_due";
    public static final String STATUS_TRIALING = "trialing";
    public static final String STATUS_EXPIRED = "expired";  // Trial ended, no payment
    public static final String STATUS_BLOCKED = "blocked";  // Account blocked for non-payment
    
    // Tier constants
    public static final String TIER_FREE = "FREE";
    public static final String TIER_STARTER = "STARTER";
    public static final String TIER_PRO = "PRO";
    public static final String TIER_ENTERPRISE = "ENTERPRISE";
    
    // Default trial days (can be configured)
    public static final int DEFAULT_TRIAL_DAYS = 5;
    
    public Subscription() {
        this.createdAt = new Date();
        this.updatedAt = new Date();
        this.status = STATUS_TRIALING;
        this.tier = TIER_FREE;
        this.cancelAtPeriodEnd = false;
    }
    
    public Subscription(String managerId, String tier) {
        this();
        this.managerId = managerId;
        this.tier = tier;
    }
    
    // ==================== Status Check Methods ====================
    
    /**
     * Check if subscription allows access to the platform
     * Returns true if: active, trialing (within trial period), or ENTERPRISE
     */
    public boolean canAccess() {
        // ENTERPRISE tier always has access (test accounts)
        if (TIER_ENTERPRISE.equals(tier)) {
            return true;
        }
        
        // Active paid subscription
        if (STATUS_ACTIVE.equals(status)) {
            return true;
        }
        
        // Within trial period
        if (isTrialing()) {
            return true;
        }
        
        // All other statuses: blocked, expired, cancelled, past_due
        return false;
    }
    
    /**
     * Check if currently in active trial period
     */
    public boolean isTrialing() {
        if (!STATUS_TRIALING.equals(status)) {
            return false;
        }
        if (trialEnd == null) {
            return false;
        }
        return new Date().before(trialEnd);
    }
    
    /**
     * Check if trial has expired (was trialing but now past trial end date)
     */
    public boolean isTrialExpired() {
        if (!STATUS_TRIALING.equals(status) && !STATUS_EXPIRED.equals(status)) {
            return false;
        }
        if (trialEnd == null) {
            return false;
        }
        return new Date().after(trialEnd);
    }
    
    /**
     * Check if subscription is active (paid and current)
     */
    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }
    
    /**
     * Check if subscription period has expired
     */
    public boolean isExpired() {
        return currentPeriodEnd != null && new Date().after(currentPeriodEnd);
    }
    
    /**
     * Check if account is blocked
     */
    public boolean isBlocked() {
        return STATUS_BLOCKED.equals(status) || STATUS_EXPIRED.equals(status);
    }
    
    /**
     * Get days remaining in trial
     */
    public int getTrialDaysRemaining() {
        if (trialEnd == null) return 0;
        long diffMs = trialEnd.getTime() - new Date().getTime();
        if (diffMs <= 0) return 0;
        return (int) (diffMs / (1000 * 60 * 60 * 24));
    }
    
    /**
     * Get human-readable status message
     */
    public String getStatusMessage() {
        if (TIER_ENTERPRISE.equals(tier)) {
            return "企业版 - 无限制";
        }
        
        switch (status) {
            case STATUS_ACTIVE:
                return "已激活";
            case STATUS_TRIALING:
                if (isTrialing()) {
                    return "试用中 - 剩余 " + getTrialDaysRemaining() + " 天";
                } else {
                    return "试用已过期 - 请升级";
                }
            case STATUS_EXPIRED:
                return "已过期 - 请续费";
            case STATUS_BLOCKED:
                return "账户已冻结 - 请联系客服";
            case STATUS_PAST_DUE:
                return "付款逾期 - 请尽快付款";
            case STATUS_CANCELLED:
                return "已取消";
            default:
                return "未知状态";
        }
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }
    
    public String getTier() { return tier; }
    public void setTier(String tier) { 
        this.tier = tier; 
        this.updatedAt = new Date();
    }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { 
        this.status = status; 
        this.updatedAt = new Date();
    }
    
    public Date getCurrentPeriodStart() { return currentPeriodStart; }
    public void setCurrentPeriodStart(Date currentPeriodStart) { this.currentPeriodStart = currentPeriodStart; }
    
    public Date getCurrentPeriodEnd() { return currentPeriodEnd; }
    public void setCurrentPeriodEnd(Date currentPeriodEnd) { this.currentPeriodEnd = currentPeriodEnd; }
    
    public Date getTrialEnd() { return trialEnd; }
    public void setTrialEnd(Date trialEnd) { this.trialEnd = trialEnd; }
    
    public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
    public void setCancelAtPeriodEnd(boolean cancelAtPeriodEnd) { this.cancelAtPeriodEnd = cancelAtPeriodEnd; }
    
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
