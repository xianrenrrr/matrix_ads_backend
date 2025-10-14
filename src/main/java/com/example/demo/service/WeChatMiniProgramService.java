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
        
        // Request new token from WeChat API
        String url = String.format(
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
            appId, appSecret
        );
        
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
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
        } catch (Exception e) {
            log.error("❌ Failed to get WeChat access token", e);
            throw new Exception("Failed to get WeChat access token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate WeChat Mini Program QR Code using URL Scheme
     * This QR code can be scanned by regular WeChat app (not just mini program scanner)
     * 
     * @param token Group invite token
     * @return QR code image URL or data
     */
    public String generateMiniProgramQRCode(String token) throws Exception {
        String accessToken = getAccessToken();
        
        // WeChat Mini Program URL Scheme API
        String url = "https://api.weixin.qq.com/wxa/generatescheme?access_token=" + accessToken;
        
        // Build request body
        Map<String, Object> requestBody = new HashMap<>();
        
        // Jump to signup page with token parameter
        Map<String, Object> jumpWxa = new HashMap<>();
        jumpWxa.put("path", "pages/signup/signup");
        jumpWxa.put("query", "token=" + token);
        
        requestBody.put("jump_wxa", jumpWxa);
        requestBody.put("is_expire", false); // Never expire
        
        log.info("Generating WeChat Mini Program URL Scheme for token: {}", token);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> result = objectMapper.readValue(
                    response.getBody(),
                    Map.class
                );
                
                if (result.containsKey("openlink")) {
                    String openlink = (String) result.get("openlink");
                    log.info("✅ WeChat URL Scheme generated: {}", openlink);
                    
                    // Generate QR code from the URL Scheme
                    return generateQRCodeFromUrl(openlink);
                } else {
                    Integer errcode = (Integer) result.get("errcode");
                    String errmsg = (String) result.get("errmsg");
                    log.error("❌ WeChat API error: {} - {}", errcode, errmsg);
                    
                    // Fallback to old method if API fails
                    return generateFallbackQRCode(token);
                }
            } else {
                log.error("❌ Failed to generate URL Scheme: HTTP {}", response.getStatusCode());
                return generateFallbackQRCode(token);
            }
        } catch (Exception e) {
            log.error("❌ Exception generating WeChat QR code", e);
            return generateFallbackQRCode(token);
        }
    }
    
    /**
     * Generate QR code image from WeChat URL Scheme
     */
    private String generateQRCodeFromUrl(String urlScheme) {
        try {
            String encodedUrl = java.net.URLEncoder.encode(urlScheme, "UTF-8");
            // Use QR code generation service
            return "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + encodedUrl;
        } catch (Exception e) {
            log.error("Failed to encode URL for QR code", e);
            return "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + urlScheme;
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
