package com.example.demo.service;

import com.example.demo.model.billing.Invoice;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/**
 * WeChat Pay Native Payment Service
 * Generates QR codes for managers to scan and pay invoices
 * 
 * Documentation: https://pay.weixin.qq.com/wiki/doc/apiv3/apis/chapter3_4_1.shtml
 * 
 * ============================================================================
 * TODO: WECHAT PAY SETUP CHECKLIST
 * ============================================================================
 * 
 * 1. MERCHANT ACCOUNT SETUP:
 *    - TODO: Register WeChat Pay merchant account at https://pay.weixin.qq.com
 *    - TODO: Complete business verification
 *    - TODO: Get merchant ID (mchid)
 * 
 * 2. API CREDENTIALS:
 *    - TODO: Generate API v3 key in WeChat Pay dashboard
 *    - TODO: Download merchant certificate (.pem files)
 *    - TODO: Get certificate serial number
 * 
 * 3. APPLICATION.PROPERTIES CONFIG:
 *    wechat.pay.appid=your-wechat-appid
 *    wechat.pay.mchid=your-merchant-id
 *    wechat.pay.api-key=your-api-v3-key
 *    wechat.pay.notify-url=https://your-domain.com/api/billing/pay/callback
 *    wechat.pay.serial-no=your-certificate-serial-number
 * 
 * 4. CERTIFICATE SETUP:
 *    - TODO: Store merchant private key securely (not in git!)
 *    - TODO: Implement RSA-SHA256 signature using private key
 *    - TODO: Implement callback signature verification
 * 
 * 5. TESTING:
 *    - TODO: Test with WeChat Pay sandbox first
 *    - TODO: Test payment flow end-to-end
 *    - TODO: Test callback handling
 * 
 * ============================================================================
 */
@Service
public class WeChatPayService {
    
    // WeChat Pay API v3 credentials (configure in application.properties)
    @Value("${wechat.pay.appid:}")
    private String appId;
    
    @Value("${wechat.pay.mchid:}")
    private String mchId;  // Merchant ID
    
    @Value("${wechat.pay.api-key:}")
    private String apiKey;  // API v3 key
    
    @Value("${wechat.pay.notify-url:}")
    private String notifyUrl;  // Payment callback URL
    
    @Value("${wechat.pay.serial-no:}")
    private String serialNo;  // Certificate serial number
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // WeChat Pay API endpoints
    private static final String NATIVE_PAY_URL = "https://api.mch.weixin.qq.com/v3/pay/transactions/native";
    private static final String QUERY_ORDER_URL = "https://api.mch.weixin.qq.com/v3/pay/transactions/out-trade-no/";
    
    /**
     * Create a Native Payment order and get QR code URL
     * Manager scans this QR code with WeChat to pay
     * 
     * @param invoice The invoice to pay
     * @param description Payment description shown to user
     * @return QR code URL (weixin://wxpay/bizpayurl?pr=xxx)
     */
    public PaymentResult createNativePayment(Invoice invoice, String description) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("WeChat Pay is not configured. Please set wechat.pay.* properties.");
        }
        
        // Build order request
        Map<String, Object> request = new HashMap<>();
        request.put("appid", appId);
        request.put("mchid", mchId);
        request.put("description", description);
        request.put("out_trade_no", invoice.getId());  // Use invoice ID as order number
        request.put("notify_url", notifyUrl);
        
        // Amount in cents (分)
        Map<String, Object> amount = new HashMap<>();
        amount.put("total", invoice.getTotal());
        amount.put("currency", "CNY");
        request.put("amount", amount);
        
        // Optional: Set expiration time (2 hours)
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, 2);
        request.put("time_expire", formatISO8601(cal.getTime()));
        
        // Send request to WeChat Pay
        String requestBody = objectMapper.writeValueAsString(request);
        String response = sendRequest("POST", NATIVE_PAY_URL, requestBody);
        
        // Parse response
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        
        if (responseMap.containsKey("code_url")) {
            String codeUrl = (String) responseMap.get("code_url");
            
            PaymentResult result = new PaymentResult();
            result.setSuccess(true);
            result.setCodeUrl(codeUrl);  // This is the QR code content
            result.setOutTradeNo(invoice.getId());
            result.setQrCodeBase64(generateQRCodeBase64(codeUrl));
            return result;
        } else {
            PaymentResult result = new PaymentResult();
            result.setSuccess(false);
            result.setErrorCode((String) responseMap.get("code"));
            result.setErrorMessage((String) responseMap.get("message"));
            return result;
        }
    }
    
    /**
     * Query payment status
     * 
     * @param outTradeNo The invoice/order ID
     * @return Payment status
     */
    public PaymentStatus queryPaymentStatus(String outTradeNo) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("WeChat Pay is not configured.");
        }
        
        String url = QUERY_ORDER_URL + outTradeNo + "?mchid=" + mchId;
        String response = sendRequest("GET", url, null);
        
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        
        PaymentStatus status = new PaymentStatus();
        status.setOutTradeNo(outTradeNo);
        status.setTradeState((String) responseMap.get("trade_state"));
        status.setTradeStateDesc((String) responseMap.get("trade_state_desc"));
        status.setTransactionId((String) responseMap.get("transaction_id"));
        
        if (responseMap.containsKey("success_time")) {
            status.setSuccessTime((String) responseMap.get("success_time"));
        }
        
        return status;
    }
    
    /**
     * Handle payment notification callback from WeChat
     * Called when user completes payment
     * 
     * @param requestBody The callback request body
     * @param signature WeChat signature for verification
     * @return Invoice ID if payment successful
     */
    public String handlePaymentCallback(String requestBody, String signature, String timestamp, String nonce) throws Exception {
        // TODO: Verify signature using WeChat Pay certificate
        // For now, parse the callback data
        
        Map<String, Object> callback = objectMapper.readValue(requestBody, Map.class);
        
        if ("TRANSACTION.SUCCESS".equals(callback.get("event_type"))) {
            Map<String, Object> resource = (Map<String, Object>) callback.get("resource");
            // Decrypt resource.ciphertext using API key
            // Extract out_trade_no (invoice ID)
            
            String outTradeNo = (String) resource.get("out_trade_no");
            return outTradeNo;
        }
        
        return null;
    }
    
    /**
     * Check if WeChat Pay is configured
     */
    public boolean isConfigured() {
        return appId != null && !appId.isEmpty() 
            && mchId != null && !mchId.isEmpty()
            && apiKey != null && !apiKey.isEmpty();
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Send HTTP request to WeChat Pay API with signature
     */
    private String sendRequest(String method, String urlString, String body) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "MatrixAds/1.0");
        
        // Add authorization header
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonceStr = generateNonceStr();
        String signature = generateSignature(method, urlString, timestamp, nonceStr, body);
        
        String authorization = String.format(
            "WECHATPAY2-SHA256-RSA2048 mchid=\"%s\",nonce_str=\"%s\",signature=\"%s\",timestamp=\"%s\",serial_no=\"%s\"",
            mchId, nonceStr, signature, timestamp, serialNo
        );
        conn.setRequestProperty("Authorization", authorization);
        
        if (body != null && !body.isEmpty()) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        
        // Read response
        int responseCode = conn.getResponseCode();
        BufferedReader reader;
        if (responseCode >= 200 && responseCode < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
        }
        
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        
        return response.toString();
    }
    
    /**
     * Generate signature for WeChat Pay API v3
     */
    private String generateSignature(String method, String url, String timestamp, String nonceStr, String body) throws Exception {
        // Build signature string
        String signStr = method + "\n" 
            + new URL(url).getPath() + "\n"
            + timestamp + "\n"
            + nonceStr + "\n"
            + (body != null ? body : "") + "\n";
        
        // TODO: Sign with merchant private key (RSA-SHA256)
        // For now, return placeholder - need to implement RSA signing
        return Base64.getEncoder().encodeToString(signStr.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Generate random nonce string
     */
    private String generateNonceStr() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * Format date as ISO8601 for WeChat Pay
     */
    private String formatISO8601(Date date) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return sdf.format(date);
    }
    
    /**
     * Generate QR code as Base64 image
     * Uses a simple QR code library or external service
     */
    private String generateQRCodeBase64(String content) {
        // TODO: Implement QR code generation using ZXing or similar library
        // For now, return the raw URL - frontend can generate QR code
        return null;
    }
    
    // ==================== Result Classes ====================
    
    /**
     * Payment creation result
     */
    public static class PaymentResult {
        private boolean success;
        private String codeUrl;      // QR code URL (weixin://wxpay/...)
        private String qrCodeBase64; // QR code as base64 image
        private String outTradeNo;   // Order/Invoice ID
        private String errorCode;
        private String errorMessage;
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getCodeUrl() { return codeUrl; }
        public void setCodeUrl(String codeUrl) { this.codeUrl = codeUrl; }
        
        public String getQrCodeBase64() { return qrCodeBase64; }
        public void setQrCodeBase64(String qrCodeBase64) { this.qrCodeBase64 = qrCodeBase64; }
        
        public String getOutTradeNo() { return outTradeNo; }
        public void setOutTradeNo(String outTradeNo) { this.outTradeNo = outTradeNo; }
        
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
    
    /**
     * Payment status query result
     */
    public static class PaymentStatus {
        private String outTradeNo;
        private String transactionId;  // WeChat payment ID
        private String tradeState;     // SUCCESS, NOTPAY, CLOSED, etc.
        private String tradeStateDesc;
        private String successTime;
        
        public boolean isPaid() { return "SUCCESS".equals(tradeState); }
        
        public String getOutTradeNo() { return outTradeNo; }
        public void setOutTradeNo(String outTradeNo) { this.outTradeNo = outTradeNo; }
        
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        
        public String getTradeState() { return tradeState; }
        public void setTradeState(String tradeState) { this.tradeState = tradeState; }
        
        public String getTradeStateDesc() { return tradeStateDesc; }
        public void setTradeStateDesc(String tradeStateDesc) { this.tradeStateDesc = tradeStateDesc; }
        
        public String getSuccessTime() { return successTime; }
        public void setSuccessTime(String successTime) { this.successTime = successTime; }
    }
}
