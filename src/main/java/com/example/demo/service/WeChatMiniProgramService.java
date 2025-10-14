package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WeChat Mini Program Service
 * Handles WeChat Mini Program API interactions including:
 * - Access token management
 * - QR code generation (WeChat URL Scheme)
 */
@Service
public class WeChatMiniProgramService {
    
    private static final Logger log = LoggerFactory.getLogger(WeChatMiniProgramService.class);
    
    @Value("${wechat.miniprogram.appid:wx351e2b055747fa78}")
    private String appId;
    
    @Value("${wechat.miniprogram.secret:${MINI_APP_SECRET:}}")
    private String appSecret;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Cache for access token
    private static class AccessTokenCache {
        String token;
        long expiresAt;
        
        boolean isValid() {
            return token != null && System.currentTimeMillis() < expiresAt;
        }
    }
    
    private final AccessTokenCache accessTokenCache = new AccessTokenCache();
    
    /**
     * Get WeChat access token (cached)
     * Token is valid for 2 hours, we refresh 5 minutes before expiry
     */
    public String getAccessToken() throws Exception {
        // Return cached token if still valid
        if (accessTokenCache.isValid()) {
            return accessTokenCache.token;
        }
        
        // Validate configuration
        if (appSecret == null || appSecret.isEmpty()) {
            throw new IllegalStateException(
                "WeChat Mini Program AppSecret not configured. " +
                "Please set MINI_APP_SECRET environment variable on Render."
            );
        }
        
        log.info("Fetching new WeChat access token...");
        log.info("AppID: {}", appId);
        log.info("AppSecret configured: {}", (appSecret != null && !appSecret.isEmpty()));
        
        // Request new token from WeChat API
        String url = String.format(
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
            appId, appSecret
        );
        
        log.info("Calling WeChat token API...");
        
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            log.info("WeChat token API response status: {}", response.getStatusCode());
            
            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> result = objectMapper.readValue(
                    response.getBody(), 
                    Map.class
                );
                
                if (result.containsKey("access_token")) {
                    String token = (String) result.get("access_token");
                    Integer expiresIn = (Integer) result.get("expires_in"); // Usually 7200 seconds (2 hours)
                    
                    // Cache token, refresh 5 minutes before expiry
                    accessTokenCache.token = token;
                    accessTokenCache.expiresAt = System.currentTimeMillis() + ((expiresIn - 300) * 1000L);
                    
                    log.info("✅ WeChat access token obtained, expires in {} seconds", expiresIn);
                    return token;
                } else {
                    Integer errcode = (Integer) result.get("errcode");
                    String errmsg = (String) result.get("errmsg");
                    throw new Exception("WeChat API error: " + errcode + " - " + errmsg);
                }
            } else {
                throw new Exception("Failed to get access token: HTTP " + response.getStatusCode());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("❌ WeChat API HTTP Error: {} - {}", e.getStatusCode(), e.getMessage());
            log.error("Response body: {}", e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 412) {
                throw new Exception("WeChat API 412 Error - Possible causes: Wrong AppSecret, IP not whitelisted, or mini program not verified", e);
            }
            throw new Exception("Failed to get WeChat access token: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Failed to get WeChat access token", e);
            throw new Exception("Failed to get WeChat access token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate WeChat Mini Program QR Code using QR Code API (not URL Scheme)
     * This QR code can be scanned by regular WeChat app
     * 
     * Note: Using getUnlimitedQRCode API instead of generateScheme because:
     * - More widely supported (doesn't require special permissions)
     * - Works for all mini programs (published or not)
     * - Returns actual QR code image (not URL)
     * 
     * @param token Group invite token
     * @return QR code image URL or data
     */
    public String generateMiniProgramQRCode(String token) throws Exception {
        log.info("🔍 Starting WeChat QR code generation for token: {}", token);
        
        String accessToken;
        try {
            accessToken = getAccessToken();
            log.info("✅ Got access token: {}...", accessToken.substring(0, Math.min(10, accessToken.length())));
        } catch (Exception e) {
            log.error("❌ Failed to get access token: {}", e.getMessage());
            throw e;
        }
        
        // WeChat Mini Program QR Code API (getUnlimitedQRCode)
        // This API is more widely supported than URL Scheme
        String url = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + accessToken;
        log.info("📡 Calling WeChat QR Code API: {}", url.substring(0, url.indexOf("access_token=") + 20) + "...");
        
        // Build request body for QR Code API
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("scene", "token=" + token); // Scene parameter (max 32 chars visible, but can be longer)
        requestBody.put("page", "pages/signup/signup"); // Target page
        requestBody.put("width", 280); // QR code width
        requestBody.put("auto_color", false);
        requestBody.put("is_hyaline", false);
        
        log.info("Generating WeChat Mini Program QR Code for token: {}", token);
        log.info("📤 Request body: {}", requestBody);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            log.info("🚀 Sending POST request to WeChat QR Code API...");
            
            // This API returns binary image data (not JSON)
            ResponseEntity<byte[]> response = restTemplate.postForEntity(url, request, byte[].class);
            log.info("📥 Response status: {}", response.getStatusCode());
            log.info("📥 Response content type: {}", response.getHeaders().getContentType());
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                byte[] imageData = response.getBody();
                
                // Check if response is actually an error (JSON) instead of image
                if (imageData.length > 0 && imageData[0] == '{') {
                    // It's JSON error response
                    String errorJson = new String(imageData);
                    log.error("❌ WeChat API returned error: {}", errorJson);
                    return generateFallbackQRCode(token);
                }
                
                log.info("✅ WeChat QR Code generated successfully, size: {} bytes", imageData.length);
                
                // Convert to base64 data URL for immediate use
                String base64Image = java.util.Base64.getEncoder().encodeToString(imageData);
                String dataUrl = "data:image/jpeg;base64," + base64Image;
                
                log.info("✅ QR Code converted to data URL");
                
                return dataUrl;
            } else {
                log.error("❌ Failed to generate QR Code: HTTP {}", response.getStatusCode());
                return generateFallbackQRCode(token);
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("❌ WeChat URL Scheme API Error: {} - {}", e.getStatusCode(), e.getMessage());
            log.error("Response body: {}", e.getResponseBodyAsString());
            log.error("Request was: POST {} with body: {}", url.substring(0, Math.min(100, url.length())), requestBody);
            
            if (e.getStatusCode().value() == 412) {
                log.error("⚠️ HTTP 412 Error - Possible causes:");
                log.error("  1. URL Scheme API not enabled in WeChat admin panel");
                log.error("  2. Mini program not published/verified");
                log.error("  3. Path 'pages/signup/signup' doesn't exist in mini program");
                log.error("  4. Mini program configuration incomplete");
            }
            
            return generateFallbackQRCode(token);
        } catch (Exception e) {
            log.error("❌ Exception generating WeChat QR code: {} - {}", e.getClass().getName(), e.getMessage());
            log.error("Full stack trace:", e);
            return generateFallbackQRCode(token);
        }
    }
    

    
    /**
     * Fallback QR code generation (old method)
     * Used if WeChat API fails
     */
    private String generateFallbackQRCode(String token) {
        log.warn("⚠️ Using fallback QR code generation");
        String miniProgramPath = "pages/signup/signup?token=" + token;
        try {
            String encodedPath = java.net.URLEncoder.encode(miniProgramPath, "UTF-8");
            return "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + encodedPath;
        } catch (Exception e) {
            return "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + miniProgramPath;
        }
    }
    
    /**
     * Check if WeChat Mini Program service is properly configured
     */
    public boolean isConfigured() {
        return appSecret != null && !appSecret.isEmpty();
    }
    
    /**
     * Get configuration status for debugging
     */
    public Map<String, Object> getConfigStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("appId", appId);
        status.put("secretConfigured", appSecret != null && !appSecret.isEmpty());
        status.put("accessTokenCached", accessTokenCache.isValid());
        return status;
    }
}
