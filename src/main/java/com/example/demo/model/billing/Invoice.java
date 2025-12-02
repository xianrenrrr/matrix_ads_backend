package com.example.demo.model.billing;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Invoice model for billing records
 */
public class Invoice {
    private String id;
    private String managerId;
    private String subscriptionId;
    private String status;  // draft, open, paid, void, uncollectible
    private Date periodStart;
    private Date periodEnd;
    private int subtotal;      // In cents/分
    private int tax;           // In cents/分
    private int total;         // In cents/分
    private String currency;   // CNY, USD, etc.
    private List<LineItem> lineItems;
    private Date paidAt;
    private String paymentMethod;  // alipay, wechat, stripe
    private String paymentId;      // External payment reference
    private Date createdAt;
    private Date updatedAt;
    
    // Status constants
    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_OPEN = "open";
    public static final String STATUS_PAID = "paid";
    public static final String STATUS_VOID = "void";
    public static final String STATUS_UNCOLLECTIBLE = "uncollectible";
    
    public Invoice() {
        this.createdAt = new Date();
        this.updatedAt = new Date();
        this.status = STATUS_DRAFT;
        this.currency = "CNY";
        this.lineItems = new ArrayList<>();
    }
    
    public Invoice(String managerId, String subscriptionId, Date periodStart, Date periodEnd) {
        this();
        this.managerId = managerId;
        this.subscriptionId = subscriptionId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }
    
    // Helper methods
    public void addLineItem(String description, int amount, int quantity) {
        lineItems.add(new LineItem(description, amount, quantity));
        recalculateTotal();
    }
    
    public void recalculateTotal() {
        this.subtotal = lineItems.stream()
            .mapToInt(item -> item.getAmount() * item.getQuantity())
            .sum();
        this.total = this.subtotal + this.tax;
        this.updatedAt = new Date();
    }
    
    public void markAsPaid(String paymentMethod, String paymentId) {
        this.status = STATUS_PAID;
        this.paidAt = new Date();
        this.paymentMethod = paymentMethod;
        this.paymentId = paymentId;
        this.updatedAt = new Date();
    }
    
    public boolean isPaid() {
        return STATUS_PAID.equals(status);
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }
    
    public String getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { 
        this.status = status; 
        this.updatedAt = new Date();
    }
    
    public Date getPeriodStart() { return periodStart; }
    public void setPeriodStart(Date periodStart) { this.periodStart = periodStart; }
    
    public Date getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Date periodEnd) { this.periodEnd = periodEnd; }
    
    public int getSubtotal() { return subtotal; }
    public void setSubtotal(int subtotal) { this.subtotal = subtotal; }
    
    public int getTax() { return tax; }
    public void setTax(int tax) { this.tax = tax; }
    
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public List<LineItem> getLineItems() { return lineItems; }
    public void setLineItems(List<LineItem> lineItems) { this.lineItems = lineItems; }
    
    public Date getPaidAt() { return paidAt; }
    public void setPaidAt(Date paidAt) { this.paidAt = paidAt; }
    
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    
    /**
     * Line item for invoice
     */
    public static class LineItem {
        private String description;
        private int amount;      // Unit price in cents/分
        private int quantity;
        
        public LineItem() {}
        
        public LineItem(String description, int amount, int quantity) {
            this.description = description;
            this.amount = amount;
            this.quantity = quantity;
        }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }
        
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        
        public int getTotal() { return amount * quantity; }
    }
}
