package com.example.demo.controller;

import com.example.demo.model.billing.*;
import com.example.demo.service.BillingService;
import com.example.demo.service.WeChatPayService;
import com.example.demo.service.WeChatPayService.PaymentResult;
import com.example.demo.service.WeChatPayService.PaymentStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Billing Controller
 * Handles subscription management, usage tracking, and WeChat Pay integration
 */
@RestController
@RequestMapping("/api/billing")
public class BillingController {
    
    @Autowired
    private BillingService billingService;
    
    @Autowired
    private WeChatPayService weChatPayService;
    
    // ==================== Subscription Endpoints ====================
    
    /**
     * Create a new subscription (starts with FREE trial)
     */
    @PostMapping("/subscriptions")
    public ResponseEntity<Map<String, Object>> createSubscription(@RequestBody Map<String, String> request) {
        String managerId = request.get("managerId");
        
        if (managerId == null || managerId.isEmpty()) {
            return ResponseEntity.badRequest().body(errorResponse("managerId is required"));
        }
        
        Subscription subscription = billingService.createSubscription(managerId);
        
        return ResponseEntity.ok(successResponse("Subscription created", subscription));
    }
    
    /**
     * Get subscription details
     */
    @GetMapping("/subscriptions/{managerId}")
    public ResponseEntity<Map<String, Object>> getSubscription(@PathVariable String managerId) {
        // TODO: Fetch from database
        // For now, return mock data
        Map<String, Object> data = new HashMap<>();
        data.put("managerId", managerId);
        data.put("tier", "FREE");
        data.put("status", "trialing");
        
        return ResponseEntity.ok(successResponse("Subscription retrieved", data));
    }
    
    /**
     * Check if user can access the platform
     * Frontend should call this on page load to check subscription status
     * 
     * Returns:
     * - canAccess: true/false
     * - code: ACTIVE, TRIALING, TRIAL_EXPIRED, BLOCKED, EXPIRED
     * - message: Human-readable message
     * - daysRemaining: Days left in trial (if trialing)
     */
    @GetMapping("/access/{managerId}")
    public ResponseEntity<Map<String, Object>> checkAccess(@PathVariable String managerId) {
        // TODO: Fetch subscription from database
        // Subscription subscription = subscriptionDao.findByManagerId(managerId);
        
        // For now, create a mock subscription in trial
        Subscription mockSub = billingService.createSubscription(managerId, 5);
        
        BillingService.AccessCheckResult result = billingService.checkAccess(mockSub);
        
        Map<String, Object> data = new HashMap<>();
        data.put("managerId", managerId);
        data.put("canAccess", result.canAccess());
        data.put("code", result.getCode());
        data.put("message", result.getMessage());
        data.put("tier", mockSub.getTier());
        data.put("status", mockSub.getStatus());
        data.put("daysRemaining", mockSub.getTrialDaysRemaining());
        data.put("statusMessage", mockSub.getStatusMessage());
        
        if (!result.canAccess()) {
            // Return 403 if access denied
            return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "error", result.getMessage(),
                "code", result.getCode(),
                "data", data
            ));
        }
        
        return ResponseEntity.ok(successResponse("Access granted", data));
    }
    
    /**
     * Upgrade/downgrade subscription tier
     */
    @PutMapping("/subscriptions/{managerId}/tier")
    public ResponseEntity<Map<String, Object>> changeTier(
            @PathVariable String managerId,
            @RequestBody Map<String, String> request) {
        
        String newTier = request.get("tier");
        if (newTier == null || newTier.isEmpty()) {
            return ResponseEntity.badRequest().body(errorResponse("tier is required"));
        }
        
        // Validate tier
        if (!isValidTier(newTier)) {
            return ResponseEntity.badRequest().body(errorResponse("Invalid tier. Must be FREE, STARTER, PRO, or ENTERPRISE"));
        }
        
        // TODO: Fetch subscription from database and update
        Map<String, Object> data = new HashMap<>();
        data.put("managerId", managerId);
        data.put("tier", newTier);
        data.put("status", "active");
        
        return ResponseEntity.ok(successResponse("Tier updated", data));
    }
    
    // ==================== Usage Endpoints ====================
    
    /**
     * Get current usage metrics for a manager
     */
    @GetMapping("/usage/{managerId}")
    public ResponseEntity<Map<String, Object>> getUsage(@PathVariable String managerId) {
        // TODO: Calculate actual usage from database
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("activeCreators", 0);
        metrics.put("totalCreators", 0);
        metrics.put("activeTemplates", 0);
        metrics.put("totalTemplates", 0);
        metrics.put("totalGroups", 0);
        metrics.put("videoSubmissions", 0);
        metrics.put("aiAnalysisCount", 0);
        metrics.put("storageUsedGB", 0.0);
        
        Map<String, Object> data = new HashMap<>();
        data.put("managerId", managerId);
        data.put("metrics", metrics);
        
        return ResponseEntity.ok(successResponse("Usage retrieved", data));
    }
    
    /**
     * Get tier limits and remaining quota
     */
    @GetMapping("/limits/{managerId}")
    public ResponseEntity<Map<String, Object>> getLimits(@PathVariable String managerId) {
        // TODO: Get actual tier from subscription
        String tier = "FREE";
        
        PricingConfig config = billingService.getPricingConfig();
        PricingConfig.TierLimits limits = config.getLimits(tier);
        
        Map<String, Object> data = new HashMap<>();
        data.put("managerId", managerId);
        data.put("tier", tier);
        data.put("limits", limits);
        
        return ResponseEntity.ok(successResponse("Limits retrieved", data));
    }
    
    // ==================== Payment Endpoints ====================
    
    /**
     * Create a payment QR code for an invoice
     * Returns a WeChat Pay QR code URL that manager can scan to pay
     */
    @PostMapping("/pay/{invoiceId}")
    public ResponseEntity<Map<String, Object>> createPayment(@PathVariable String invoiceId) {
        try {
            if (!weChatPayService.isConfigured()) {
                return ResponseEntity.status(503).body(errorResponse("WeChat Pay is not configured"));
            }
            
            // TODO: Fetch invoice from database
            Invoice invoice = new Invoice();
            invoice.setId(invoiceId);
            invoice.setTotal(99900);  // ¥999.00 in cents
            
            String description = "Matrix Ads 订阅 - " + invoiceId;
            PaymentResult result = weChatPayService.createNativePayment(invoice, description);
            
            if (result.isSuccess()) {
                Map<String, Object> data = new HashMap<>();
                data.put("invoiceId", invoiceId);
                data.put("codeUrl", result.getCodeUrl());  // QR code content
                data.put("qrCodeBase64", result.getQrCodeBase64());  // QR code image
                data.put("outTradeNo", result.getOutTradeNo());
                
                return ResponseEntity.ok(successResponse("Payment QR code created", data));
            } else {
                return ResponseEntity.badRequest().body(errorResponse(
                    "Payment creation failed: " + result.getErrorCode() + " - " + result.getErrorMessage()
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(errorResponse("Payment error: " + e.getMessage()));
        }
    }
    
    /**
     * Query payment status
     */
    @GetMapping("/pay/status/{outTradeNo}")
    public ResponseEntity<Map<String, Object>> queryPaymentStatus(@PathVariable String outTradeNo) {
        try {
            if (!weChatPayService.isConfigured()) {
                return ResponseEntity.status(503).body(errorResponse("WeChat Pay is not configured"));
            }
            
            PaymentStatus status = weChatPayService.queryPaymentStatus(outTradeNo);
            
            Map<String, Object> data = new HashMap<>();
            data.put("outTradeNo", status.getOutTradeNo());
            data.put("transactionId", status.getTransactionId());
            data.put("tradeState", status.getTradeState());
            data.put("tradeStateDesc", status.getTradeStateDesc());
            data.put("isPaid", status.isPaid());
            data.put("successTime", status.getSuccessTime());
            
            return ResponseEntity.ok(successResponse("Payment status retrieved", data));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(errorResponse("Query error: " + e.getMessage()));
        }
    }
    
    /**
     * WeChat Pay callback endpoint
     * Called by WeChat when payment is completed
     * 
     * This is where we:
     * 1. Verify the payment
     * 2. Update invoice status to PAID
     * 3. Activate the subscription
     * 4. Update user's subscription tier
     */
    @PostMapping("/pay/callback")
    public ResponseEntity<Map<String, Object>> paymentCallback(
            @RequestBody String body,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce) {
        
        try {
            String invoiceId = weChatPayService.handlePaymentCallback(body, signature, timestamp, nonce);
            
            if (invoiceId != null) {
                System.out.println("[WeChatPay] ✅ Payment SUCCESS for invoice: " + invoiceId);
                
                // TODO: Implement these with actual database operations:
                // 1. Fetch invoice from database
                // Invoice invoice = invoiceDao.findById(invoiceId);
                
                // 2. Mark invoice as paid
                // invoice.markAsPaid("wechat", transactionId);
                // invoiceDao.save(invoice);
                
                // 3. Activate subscription
                // Subscription sub = subscriptionDao.findById(invoice.getSubscriptionId());
                // sub.setStatus(Subscription.STATUS_ACTIVE);
                // subscriptionDao.save(sub);
                
                // 4. Update user's cached subscription tier
                // User user = userDao.findById(invoice.getManagerId());
                // user.setSubscriptionTier(sub.getTier());
                // userDao.save(user);
                
                System.out.println("[WeChatPay] Subscription activated for invoice: " + invoiceId);
                
                // Return success to WeChat (required format)
                Map<String, Object> response = new HashMap<>();
                response.put("code", "SUCCESS");
                response.put("message", "OK");
                return ResponseEntity.ok(response);
            } else {
                System.out.println("[WeChatPay] ❌ Invalid callback received");
                return ResponseEntity.badRequest().body(errorResponse("Invalid callback"));
            }
        } catch (Exception e) {
            System.err.println("[WeChatPay] ❌ Callback error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse("Callback error: " + e.getMessage()));
        }
    }
    
    /**
     * Check if payment is complete (for frontend polling)
     * Frontend calls this every few seconds after showing QR code
     */
    @GetMapping("/pay/check/{invoiceId}")
    public ResponseEntity<Map<String, Object>> checkPaymentComplete(@PathVariable String invoiceId) {
        // TODO: Fetch invoice from database
        // Invoice invoice = invoiceDao.findById(invoiceId);
        
        // For now, return mock data
        Map<String, Object> data = new HashMap<>();
        data.put("invoiceId", invoiceId);
        data.put("isPaid", false);  // Will be true after callback
        data.put("status", "pending");
        data.put("tier", null);  // Will be set after payment
        
        // If paid, include subscription info for UI update
        // if (invoice.isPaid()) {
        //     data.put("isPaid", true);
        //     data.put("status", "paid");
        //     data.put("tier", subscription.getTier());
        //     data.put("message", "Payment successful! Your " + tier + " membership is now active.");
        // }
        
        return ResponseEntity.ok(successResponse("Payment status", data));
    }
    
    // ==================== Pricing Endpoints ====================
    
    /**
     * Get current pricing configuration
     */
    @GetMapping("/pricing")
    public ResponseEntity<Map<String, Object>> getPricing() {
        PricingConfig config = billingService.getPricingConfig();
        return ResponseEntity.ok(successResponse("Pricing retrieved", config));
    }
    
    /**
     * Update pricing (admin only)
     */
    @PutMapping("/pricing")
    public ResponseEntity<Map<String, Object>> updatePricing(@RequestBody PricingConfig config) {
        // TODO: Add admin authentication check
        billingService.updatePricingConfig(config);
        return ResponseEntity.ok(successResponse("Pricing updated", config));
    }
    
    // ==================== Admin/Test Account Endpoints ====================
    
    /**
     * Mark a user as unlimited test account (ENTERPRISE tier)
     * Use this for your 4 test accounts
     * 
     * POST /api/billing/admin/set-unlimited
     * Body: { "userId": "xxx", "adminKey": "your-secret-key" }
     */
    @PostMapping("/admin/set-unlimited")
    public ResponseEntity<Map<String, Object>> setUnlimitedAccount(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        String adminKey = request.get("adminKey");
        
        // Simple admin key check (replace with proper auth in production)
        if (!"MATRIX_ADMIN_2024".equals(adminKey)) {
            return ResponseEntity.status(403).body(errorResponse("Invalid admin key"));
        }
        
        if (userId == null || userId.isEmpty()) {
            return ResponseEntity.badRequest().body(errorResponse("userId is required"));
        }
        
        // TODO: Update user in Firebase
        // userDao.updateSubscription(userId, "sub_unlimited_" + userId, "ENTERPRISE");
        
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("subscriptionTier", "ENTERPRISE");
        data.put("isTestAccount", true);
        data.put("message", "User marked as unlimited ENTERPRISE account");
        
        // Instructions for manual Firebase update
        data.put("firebaseUpdate", Map.of(
            "collection", "users",
            "documentId", userId,
            "fieldsToAdd", Map.of(
                "subscriptionId", "sub_unlimited_" + userId,
                "subscriptionTier", "ENTERPRISE",
                "isTestAccount", true
            )
        ));
        
        return ResponseEntity.ok(successResponse("Account set to unlimited", data));
    }
    
    /**
     * List all test/unlimited accounts
     */
    @GetMapping("/admin/test-accounts")
    public ResponseEntity<Map<String, Object>> listTestAccounts(
            @RequestParam(required = false) String adminKey) {
        
        if (!"MATRIX_ADMIN_2024".equals(adminKey)) {
            return ResponseEntity.status(403).body(errorResponse("Invalid admin key"));
        }
        
        // TODO: Query Firebase for users where isTestAccount = true
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Query Firebase: users where isTestAccount == true");
        
        return ResponseEntity.ok(successResponse("Test accounts", data));
    }
    
    // ==================== Helper Methods ====================
    
    private boolean isValidTier(String tier) {
        return Subscription.TIER_FREE.equals(tier) 
            || Subscription.TIER_STARTER.equals(tier)
            || Subscription.TIER_PRO.equals(tier)
            || Subscription.TIER_ENTERPRISE.equals(tier);
    }
    
    private Map<String, Object> successResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return response;
    }
    
    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }
}
