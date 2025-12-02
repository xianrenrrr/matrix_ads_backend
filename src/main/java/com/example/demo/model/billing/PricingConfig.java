package com.example.demo.model.billing;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * PricingConfig model for configurable pricing tiers and limits
 * This allows changing prices without code changes
 */
public class PricingConfig {
    private String id;
    private int version;
    private Date effectiveDate;
    private Map<String, TierConfig> tiers;
    private OverageRates overageRates;
    private Date createdAt;
    private Date updatedAt;
    
    public PricingConfig() {
        this.createdAt = new Date();
        this.updatedAt = new Date();
        this.tiers = new HashMap<>();
        this.overageRates = new OverageRates();
        initializeDefaultTiers();
    }
    
    /**
     * Initialize default tier configurations
     * Values marked as 0 or -1 should be updated after testing
     */
    private void initializeDefaultTiers() {
        // FREE tier (trial)
        TierConfig free = new TierConfig();
        free.setPrice(0);
        free.setLimits(new TierLimits(1, 5, 3, 1, 50, 1.0));
        free.setTrialDays(14);
        tiers.put(Subscription.TIER_FREE, free);
        
        // ================================================================
        // TODO: SET ACTUAL PRICES AFTER TESTING
        // Price is in 分 (cents), so ¥999 = 99900
        // ================================================================
        
        // STARTER tier
        // TODO: Set price after testing (e.g., 99900 for ¥999/month)
        TierConfig starter = new TierConfig();
        starter.setPrice(0);  // TODO: Set to actual price in 分
        // TODO: Adjust limits based on testing data
        starter.setLimits(new TierLimits(
            3,      // managers
            50,     // creators - TODO: adjust after testing
            20,     // templates - TODO: adjust after testing
            5,      // groups
            500,    // AI analyses per month - TODO: adjust after testing
            10.0    // storage GB
        ));
        tiers.put(Subscription.TIER_STARTER, starter);
        
        // PRO tier
        // TODO: Set price after testing (e.g., 299900 for ¥2999/month)
        TierConfig pro = new TierConfig();
        pro.setPrice(0);  // TODO: Set to actual price in 分
        // TODO: Adjust limits based on testing data
        pro.setLimits(new TierLimits(
            10,     // managers
            200,    // creators - TODO: adjust after testing
            100,    // templates - TODO: adjust after testing
            20,     // groups
            2000,   // AI analyses per month - TODO: adjust after testing
            50.0    // storage GB
        ));
        tiers.put(Subscription.TIER_PRO, pro);
        
        // ENTERPRISE tier - unlimited (for test accounts and custom deals)
        TierConfig enterprise = new TierConfig();
        enterprise.setPrice(-1);  // Custom pricing (negotiated)
        enterprise.setLimits(new TierLimits(-1, -1, -1, -1, -1, -1));  // -1 = unlimited
        tiers.put(Subscription.TIER_ENTERPRISE, enterprise);
        
        // ================================================================
        // TODO: SET OVERAGE RATES AFTER TESTING
        // ================================================================
        // overageRates.setExtraCreator(5000);     // ¥50 per extra creator
        // overageRates.setExtraTemplate(3000);    // ¥30 per extra template
        // overageRates.setExtraAiAnalysis(500);   // ¥5 per extra AI analysis
        // overageRates.setExtraStorageGB(2000);   // ¥20 per extra GB
    }
    
    // Helper methods
    public TierConfig getTierConfig(String tier) {
        return tiers.getOrDefault(tier, tiers.get(Subscription.TIER_FREE));
    }
    
    public TierLimits getLimits(String tier) {
        TierConfig config = getTierConfig(tier);
        return config != null ? config.getLimits() : null;
    }
    
    public int getPrice(String tier) {
        TierConfig config = getTierConfig(tier);
        return config != null ? config.getPrice() : 0;
    }
    
    public boolean isWithinLimit(String tier, String limitType, int currentValue) {
        TierLimits limits = getLimits(tier);
        if (limits == null) return false;
        
        int limit = limits.getLimit(limitType);
        return limit == -1 || currentValue < limit;  // -1 means unlimited
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    
    public Date getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(Date effectiveDate) { this.effectiveDate = effectiveDate; }
    
    public Map<String, TierConfig> getTiers() { return tiers; }
    public void setTiers(Map<String, TierConfig> tiers) { this.tiers = tiers; }
    
    public OverageRates getOverageRates() { return overageRates; }
    public void setOverageRates(OverageRates overageRates) { this.overageRates = overageRates; }
    
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    
    /**
     * Configuration for a single tier
     */
    public static class TierConfig {
        private int price;          // Monthly price in cents/分 (-1 = custom)
        private TierLimits limits;
        private int trialDays;      // Only for FREE tier
        
        public TierConfig() {
            this.limits = new TierLimits();
        }
        
        public int getPrice() { return price; }
        public void setPrice(int price) { this.price = price; }
        
        public TierLimits getLimits() { return limits; }
        public void setLimits(TierLimits limits) { this.limits = limits; }
        
        public int getTrialDays() { return trialDays; }
        public void setTrialDays(int trialDays) { this.trialDays = trialDays; }
    }
    
    /**
     * Limits for a tier
     */
    public static class TierLimits {
        private int managers;           // Max manager seats
        private int creators;           // Max content creators
        private int templates;          // Max templates
        private int groups;             // Max groups
        private int aiAnalysisPerMonth; // Max AI analyses per month
        private double storageGB;       // Max storage in GB
        
        public TierLimits() {}
        
        public TierLimits(int managers, int creators, int templates, int groups, int aiAnalysisPerMonth, double storageGB) {
            this.managers = managers;
            this.creators = creators;
            this.templates = templates;
            this.groups = groups;
            this.aiAnalysisPerMonth = aiAnalysisPerMonth;
            this.storageGB = storageGB;
        }
        
        public int getLimit(String type) {
            switch (type) {
                case "managers": return managers;
                case "creators": return creators;
                case "templates": return templates;
                case "groups": return groups;
                case "aiAnalysisPerMonth": return aiAnalysisPerMonth;
                case "storageGB": return (int) storageGB;
                default: return -1;
            }
        }
        
        // Getters and Setters
        public int getManagers() { return managers; }
        public void setManagers(int managers) { this.managers = managers; }
        
        public int getCreators() { return creators; }
        public void setCreators(int creators) { this.creators = creators; }
        
        public int getTemplates() { return templates; }
        public void setTemplates(int templates) { this.templates = templates; }
        
        public int getGroups() { return groups; }
        public void setGroups(int groups) { this.groups = groups; }
        
        public int getAiAnalysisPerMonth() { return aiAnalysisPerMonth; }
        public void setAiAnalysisPerMonth(int aiAnalysisPerMonth) { this.aiAnalysisPerMonth = aiAnalysisPerMonth; }
        
        public double getStorageGB() { return storageGB; }
        public void setStorageGB(double storageGB) { this.storageGB = storageGB; }
    }
    
    /**
     * Overage rates for exceeding tier limits
     */
    public static class OverageRates {
        private int extraCreator;       // Per creator per month in cents/分
        private int extraTemplate;      // Per template per month in cents/分
        private int extraAiAnalysis;    // Per AI analysis in cents/分
        private int extraStorageGB;     // Per GB per month in cents/分
        
        public OverageRates() {
            // Default to 0 - TBD after testing
            this.extraCreator = 0;
            this.extraTemplate = 0;
            this.extraAiAnalysis = 0;
            this.extraStorageGB = 0;
        }
        
        public int getExtraCreator() { return extraCreator; }
        public void setExtraCreator(int extraCreator) { this.extraCreator = extraCreator; }
        
        public int getExtraTemplate() { return extraTemplate; }
        public void setExtraTemplate(int extraTemplate) { this.extraTemplate = extraTemplate; }
        
        public int getExtraAiAnalysis() { return extraAiAnalysis; }
        public void setExtraAiAnalysis(int extraAiAnalysis) { this.extraAiAnalysis = extraAiAnalysis; }
        
        public int getExtraStorageGB() { return extraStorageGB; }
        public void setExtraStorageGB(int extraStorageGB) { this.extraStorageGB = extraStorageGB; }
    }
}
