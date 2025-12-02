package com.example.demo.service;

import com.example.demo.model.billing.*;
import com.example.demo.model.billing.UsageRecord.UsageMetrics;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;

/**
 * BillingService handles subscription management, usage tracking, and limit enforcement
 * 
 * ============================================================================
 * TODO: COMPLETE BEFORE PRODUCTION (Once pricing & WeChat merchant ready)
 * ============================================================================
 * 
 * 1. DATABASE INTEGRATION:
 *    - TODO: Create SubscriptionDao for Firebase/Firestore persistence
 *    - TODO: Create InvoiceDao for storing invoices
 *    - TODO: Create UsageRecordDao for usage tracking
 *    - TODO: Create PricingConfigDao for storing pricing config
 * 
 * 2. PRICING CONFIGURATION:
 *    - TODO: Set actual tier prices (STARTER, PRO) after testing
 *    - TODO: Set tier limits based on testing data
 *    - TODO: Set overage rates
 *    - TODO: Load pricing from database instead of in-memory
 * 
 * 3. WECHAT PAY INTEGRATION:
 *    - TODO: Get WeChat merchant account (mchid)
 *    - TODO: Get API v3 key from WeChat Pay dashboard
 *    - TODO: Upload merchant certificate and get serial number
 *    - TODO: Configure notify_url for payment callbacks
 *    - TODO: Implement RSA signature for API requests
 * 
 * 4. SCHEDULED JOBS:
 *    - TODO: Daily job to check expired trials and block access
 *    - TODO: Monthly job to generate invoices
 *    - TODO: Job to send payment reminders before trial ends
 * 
 * 5. FRONTEND INTEGRATION:
 *    - TODO: Add subscription status check on dashboard load
 *    - TODO: Add upgrade modal when trial expires
 *    - TODO: Add payment QR code display component
 *    - TODO: Add billing/subscription settings page
 * 
 * ============================================================================
 */
@Service
public class BillingService {
    
    // TODO: Replace with database-backed pricing config
    // In-memory pricing config (should be loaded from database in production)
    private PricingConfig pricingConfig;
    
    // TODO: Inject these DAOs once created
    // @Autowired private SubscriptionDao subscriptionDao;
    // @Autowired private InvoiceDao invoiceDao;
    // @Autowired private UsageRecordDao usageRecordDao;
    
    public BillingService() {
        this.pricingConfig = new PricingConfig();
        this.pricingConfig.setId("pricing_v1");
        this.pricingConfig.setVersion(1);
        this.pricingConfig.setEffectiveDate(new Date());
        
        // TODO: Load pricing from database
        // this.pricingConfig = pricingConfigDao.getLatest();
    }
    
    // ==================== Subscription Management ====================
    
    /**
     * Create a new subscription for a manager (starts with FREE trial)
     * Default: 5 days free trial
     */
    public Subscription createSubscription(String managerId) {
        return createSubscription(managerId, Subscription.DEFAULT_TRIAL_DAYS);
    }
    
    /**
     * Create a new subscription with custom trial days
     */
    public Subscription createSubscription(String managerId, int trialDays) {
        Subscription sub = new Subscription(managerId, Subscription.TIER_FREE);
        sub.setId("sub_" + System.currentTimeMillis());
        sub.setStatus(Subscription.STATUS_TRIALING);
        
        // Set trial period
        Calendar cal = Calendar.getInstance();
        sub.setCurrentPeriodStart(cal.getTime());
        cal.add(Calendar.DAY_OF_MONTH, trialDays);
        sub.setTrialEnd(cal.getTime());
        sub.setCurrentPeriodEnd(cal.getTime());
        
        System.out.println("[Billing] Created subscription for " + managerId + " with " + trialDays + " day trial");
        
        return sub;
    }
    
    /**
     * Check if a subscription allows platform access
     * This is the main gate-keeping method
     * 
     * @return AccessCheckResult with canAccess flag and message
     */
    public AccessCheckResult checkAccess(Subscription subscription) {
        if (subscription == null) {
            return new AccessCheckResult(false, "NO_SUBSCRIPTION", "请先注册账户");
        }
        
        // ENTERPRISE always has access (test accounts)
        if (Subscription.TIER_ENTERPRISE.equals(subscription.getTier())) {
            return new AccessCheckResult(true, "ENTERPRISE", "企业版账户");
        }
        
        // Check if trial expired
        if (subscription.isTrialExpired()) {
            return new AccessCheckResult(false, "TRIAL_EXPIRED", 
                "试用期已结束，请升级到付费版本继续使用");
        }
        
        // Check if blocked
        if (subscription.isBlocked()) {
            return new AccessCheckResult(false, "BLOCKED", 
                "账户已被冻结，请联系客服或完成付款");
        }
        
        // Check if active trial
        if (subscription.isTrialing()) {
            int daysLeft = subscription.getTrialDaysRemaining();
            return new AccessCheckResult(true, "TRIALING", 
                "试用中，剩余 " + daysLeft + " 天");
        }
        
        // Check if active paid subscription
        if (subscription.isActive()) {
            return new AccessCheckResult(true, "ACTIVE", "订阅有效");
        }
        
        // Check if subscription period expired
        if (subscription.isExpired()) {
            return new AccessCheckResult(false, "EXPIRED", 
                "订阅已过期，请续费继续使用");
        }
        
        // Default: no access
        return new AccessCheckResult(false, "UNKNOWN", "请联系客服");
    }
    
    /**
     * Mark subscription as expired (called when trial ends without payment)
     */
    public Subscription expireSubscription(Subscription subscription) {
        subscription.setStatus(Subscription.STATUS_EXPIRED);
        System.out.println("[Billing] Subscription expired for " + subscription.getManagerId());
        return subscription;
    }
    
    /**
     * Block subscription (for non-payment or abuse)
     */
    public Subscription blockSubscription(Subscription subscription, String reason) {
        subscription.setStatus(Subscription.STATUS_BLOCKED);
        System.out.println("[Billing] Subscription blocked for " + subscription.getManagerId() + ": " + reason);
        return subscription;
    }
    
    /**
     * Result of access check
     */
    public static class AccessCheckResult {
        private boolean canAccess;
        private String code;
        private String message;
        
        public AccessCheckResult(boolean canAccess, String code, String message) {
            this.canAccess = canAccess;
            this.code = code;
            this.message = message;
        }
        
        public boolean canAccess() { return canAccess; }
        public String getCode() { return code; }
        public String getMessage() { return message; }
    }
    
    /**
     * Upgrade or downgrade subscription tier
     */
    public Subscription changeTier(Subscription subscription, String newTier) {
        subscription.setTier(newTier);
        
        if (Subscription.TIER_FREE.equals(newTier)) {
            subscription.setStatus(Subscription.STATUS_TRIALING);
        } else {
            subscription.setStatus(Subscription.STATUS_ACTIVE);
            subscription.setTrialEnd(null);
        }
        
        // Reset billing period
        Calendar cal = Calendar.getInstance();
        subscription.setCurrentPeriodStart(cal.getTime());
        cal.add(Calendar.MONTH, 1);
        subscription.setCurrentPeriodEnd(cal.getTime());
        
        return subscription;
    }
    
    /**
     * Cancel subscription at period end
     */
    public Subscription cancelSubscription(Subscription subscription) {
        subscription.setCancelAtPeriodEnd(true);
        return subscription;
    }
    
    // ==================== Limit Checking ====================
    
    /**
     * Check if tier has unlimited access (ENTERPRISE or test accounts)
     */
    public boolean isUnlimited(String tier) {
        return Subscription.TIER_ENTERPRISE.equals(tier);
    }
    
    /**
     * Check if manager can create more templates
     */
    public boolean canCreateTemplate(String tier, int currentTemplateCount) {
        if (isUnlimited(tier)) return true;
        return pricingConfig.isWithinLimit(tier, "templates", currentTemplateCount);
    }
    
    /**
     * Check if manager can add more creators
     */
    public boolean canAddCreator(String tier, int currentCreatorCount) {
        if (isUnlimited(tier)) return true;
        return pricingConfig.isWithinLimit(tier, "creators", currentCreatorCount);
    }
    
    /**
     * Check if manager can create more groups
     */
    public boolean canCreateGroup(String tier, int currentGroupCount) {
        if (isUnlimited(tier)) return true;
        return pricingConfig.isWithinLimit(tier, "groups", currentGroupCount);
    }
    
    /**
     * Check if manager can perform more AI analyses this month
     */
    public boolean canPerformAiAnalysis(String tier, int currentAnalysisCount) {
        if (isUnlimited(tier)) return true;
        return pricingConfig.isWithinLimit(tier, "aiAnalysisPerMonth", currentAnalysisCount);
    }
    
    /**
     * Check if manager has storage available
     */
    public boolean hasStorageAvailable(String tier, double currentStorageGB, double additionalGB) {
        if (isUnlimited(tier)) return true;
        
        PricingConfig.TierLimits limits = pricingConfig.getLimits(tier);
        if (limits == null) return false;
        
        double limit = limits.getStorageGB();
        return limit == -1 || (currentStorageGB + additionalGB) <= limit;
    }
    
    /**
     * Get remaining quota for a limit type
     */
    public int getRemainingQuota(String tier, String limitType, int currentUsage) {
        PricingConfig.TierLimits limits = pricingConfig.getLimits(tier);
        if (limits == null) return 0;
        
        int limit = limits.getLimit(limitType);
        if (limit == -1) return Integer.MAX_VALUE;  // Unlimited
        return Math.max(0, limit - currentUsage);
    }
    
    // ==================== Usage Tracking ====================
    
    /**
     * Create a new usage record for a billing period
     */
    public UsageRecord createUsageRecord(String managerId, String subscriptionId) {
        Calendar cal = Calendar.getInstance();
        Date periodStart = cal.getTime();
        cal.add(Calendar.MONTH, 1);
        Date periodEnd = cal.getTime();
        
        UsageRecord record = new UsageRecord(managerId, subscriptionId, periodStart, periodEnd);
        record.setId("usage_" + System.currentTimeMillis());
        return record;
    }
    
    /**
     * Update usage metrics
     */
    public void updateUsageMetrics(UsageRecord record, UsageMetrics metrics) {
        record.setMetrics(metrics);
    }
    
    // ==================== Invoice Generation ====================
    
    /**
     * Generate invoice for a billing period
     */
    public Invoice generateInvoice(Subscription subscription, UsageRecord usage) {
        Invoice invoice = new Invoice(
            subscription.getManagerId(),
            subscription.getId(),
            usage.getPeriodStart(),
            usage.getPeriodEnd()
        );
        invoice.setId("inv_" + System.currentTimeMillis());
        
        String tier = subscription.getTier();
        int tierPrice = pricingConfig.getPrice(tier);
        
        // Add base subscription line item
        if (tierPrice > 0) {
            invoice.addLineItem(tier + " Plan - Monthly", tierPrice, 1);
        }
        
        // Calculate overage charges
        PricingConfig.TierLimits limits = pricingConfig.getLimits(tier);
        PricingConfig.OverageRates rates = pricingConfig.getOverageRates();
        UsageMetrics metrics = usage.getMetrics();
        
        if (limits != null && rates != null && metrics != null) {
            // Extra creators
            int extraCreators = Math.max(0, metrics.getTotalCreators() - limits.getCreators());
            if (extraCreators > 0 && rates.getExtraCreator() > 0) {
                invoice.addLineItem("Extra Creators", rates.getExtraCreator(), extraCreators);
            }
            
            // Extra templates
            int extraTemplates = Math.max(0, metrics.getTotalTemplates() - limits.getTemplates());
            if (extraTemplates > 0 && rates.getExtraTemplate() > 0) {
                invoice.addLineItem("Extra Templates", rates.getExtraTemplate(), extraTemplates);
            }
            
            // Extra AI analyses
            int extraAnalyses = Math.max(0, metrics.getAiAnalysisCount() - limits.getAiAnalysisPerMonth());
            if (extraAnalyses > 0 && rates.getExtraAiAnalysis() > 0) {
                invoice.addLineItem("Extra AI Analyses", rates.getExtraAiAnalysis(), extraAnalyses);
            }
            
            // Extra storage
            double extraStorageGB = Math.max(0, metrics.getStorageUsedGB() - limits.getStorageGB());
            if (extraStorageGB > 0 && rates.getExtraStorageGB() > 0) {
                invoice.addLineItem("Extra Storage (GB)", rates.getExtraStorageGB(), (int) Math.ceil(extraStorageGB));
            }
        }
        
        invoice.setStatus(Invoice.STATUS_OPEN);
        return invoice;
    }
    
    // ==================== Pricing Config ====================
    
    /**
     * Get current pricing configuration
     */
    public PricingConfig getPricingConfig() {
        return pricingConfig;
    }
    
    /**
     * Update pricing configuration
     */
    public void updatePricingConfig(PricingConfig config) {
        config.setVersion(this.pricingConfig.getVersion() + 1);
        config.setUpdatedAt(new Date());
        this.pricingConfig = config;
    }
    
    /**
     * Update tier price
     */
    public void setTierPrice(String tier, int priceInCents) {
        PricingConfig.TierConfig config = pricingConfig.getTierConfig(tier);
        if (config != null) {
            config.setPrice(priceInCents);
            pricingConfig.setUpdatedAt(new Date());
        }
    }
    
    /**
     * Update overage rates
     */
    public void setOverageRates(int extraCreator, int extraTemplate, int extraAiAnalysis, int extraStorageGB) {
        PricingConfig.OverageRates rates = pricingConfig.getOverageRates();
        rates.setExtraCreator(extraCreator);
        rates.setExtraTemplate(extraTemplate);
        rates.setExtraAiAnalysis(extraAiAnalysis);
        rates.setExtraStorageGB(extraStorageGB);
        pricingConfig.setUpdatedAt(new Date());
    }
}
