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
     * Create a short scene parameter from token (max 32 characters for WeChat)
     */
    private String createScene(String token) {
        // Remove "group_" prefix to save space
        String shortToken = token.replace("group_", "g");
        
        // Ensure scene parameter is within WeChat's 32 character limit
        String scene = "t=" + shortToken;
        if (scene.length() > 32) {
            // Take first 30 chars of token (leaving 2 for "t=")
            shortToken = shortToken.substring(0, 30);
            scene = "t=" + shortToken;
        }
        
        log.info("📏 Scene created: '{}' (length: {})", scene, scene.length());
        return scene;
    }

    /**
     * Generate WeChat Mini Program QR Code using QR Code API
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
        
        // WeChat Mini Program QR Code API
        String url = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + accessToken;
        log.info("📡 Calling WeChat QR Code API: {}", url.substring(0, url.indexOf("access_token=") + 20) + "...");
        
        // Create scene parameter (must be ≤ 32 characters)
        String scene = createScene(token);
        
        // Build request body for QR Code API
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("scene", scene); // Scene parameter (≤ 32 chars)
        requestBody.put("page", "pages/signup/signup"); // Target page
        requestBody.put("width", 430); // QR code width
        requestBody.put("auto_color", false);
        requestBody.put("is_hyaline", false);
        
        log.info("📤 Request body: {}", requestBody);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.IMAGE_PNG, MediaType.ALL));
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            log.info("🚀 Sending POST request to WeChat QR Code API...");
            
            // Use exchange for better control over response handling
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.POST, request, byte[].class);
            log.info("📥 Response status: {}", response.getStatusCode());
            
            MediaType contentType = response.getHeaders().getContentType();
            log.info("📥 Response content type: {}", contentType);
            
            byte[] responseBody = response.getBody();
            
            // Check if WeChat returned JSON error instead of image
            if (contentType != null && (contentType.includes(MediaType.APPLICATION_JSON) || 
                                      contentType.includes(MediaType.TEXT_PLAIN))) {
                String errorJson = responseBody != null ? new String(responseBody) : "No error details";
                log.error("❌ WeChat API returned JSON error: {}", errorJson);
                return generateFallbackQRCode(token);
            }
            
            if (response.getStatusCode().is2xxSuccessful() && responseBody != null && responseBody.length > 0) {
                // Additional check: if response starts with '{', it's likely JSON error
                if (responseBody[0] == '{') {
                    String errorJson = new String(responseBody);
                    log.error("❌ WeChat API returned JSON error (detected by content): {}", errorJson);
                    return generateFallbackQRCode(token);
                }
                
                log.info("✅ WeChat QR Code generated successfully, size: {} bytes", responseBody.length);
                
                // Convert to base64 data URL for immediate use
                String base64Image = java.util.Base64.getEncoder().encodeToString(responseBody);
                String dataUrl = "data:image/png;base64," + base64Image;
                
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
