package com.example.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
// Option B (if you don't want Apache HttpClient):
// import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * WeChat Mini Program Service
 * - Access token caching
 * - QR code generation via getwxacodeunlimit
 * - Buffered JSON POST to avoid "412 Precondition Failed: [no body]"
 */
@Service
public class WeChatMiniProgramService {

    private static final Logger log = LoggerFactory.getLogger(WeChatMiniProgramService.class);

    @Value("${wechat.miniprogram.appid:wx351e2b055747fa78}")
    private String appId;

    @Value("${wechat.miniprogram.secret:${MINI_APP_SECRET:}}")
    private String appSecret;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** ---- Access token cache ---- */
    private static class AccessTokenCache {
        String token;
        long expiresAtMillis;
        boolean isValid() {
            return token != null && System.currentTimeMillis() < expiresAtMillis;
        }
    }
    private final AccessTokenCache tokenCache = new AccessTokenCache();

    /** ---- Constructor: RestTemplate with buffered JSON body ---- */
    public WeChatMiniProgramService() {
        // Option A: Apache HttpClient (recommended)
        var httpClient = HttpClients.custom().build();
        var factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(10_000);
        factory.setConnectionRequestTimeout(10_000);
        factory.setReadTimeout(20_000);

        RestTemplate rt = new RestTemplate(factory);

        // Ensure JSON converter is present and prioritized
        var converters = new ArrayList<>(rt.getMessageConverters());
        converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
        converters.add(0, new MappingJackson2HttpMessageConverter());
        rt.setMessageConverters(converters);

        this.restTemplate = rt;

        // ---- Option B (no Apache) ----
        // this.restTemplate = buildBufferedRestTemplate();
    }

    // Option B: Use Spring's SimpleClientHttpRequestFactory with buffered request body
    // private RestTemplate buildBufferedRestTemplate() {
    //     var simple = new SimpleClientHttpRequestFactory();
    //     simple.setBufferRequestBody(true);        // forces Content-Length (no chunked)
    //     simple.setConnectTimeout(10_000);
    //     simple.setReadTimeout(20_000);
    //
    //     RestTemplate rt = new RestTemplate(simple);
    //     var converters = new ArrayList<>(rt.getMessageConverters());
    //     converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
    //     converters.add(0, new MappingJackson2HttpMessageConverter());
    //     rt.setMessageConverters(converters);
    //     return rt;
    // }

    /** ---- Public API ---- */

    /**
     * Generate a Mini Program QR Code.
     * @param token your long invite token or group id
     * @param forceHomepage If true, omit "page" and set "check_path=false" so it always opens the app and you route by scene.
     * @return data URL (image/png base64) on success; fallback QR URL on failure.
     */
    public String generateMiniProgramQRCode(String token, boolean forceHomepage) throws Exception {
        log.info("🔍 Start WeChat QR generation, token={}", token);

        String accessToken = getAccessToken();
        String url = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + accessToken;

        String scene = createScene(token);

        Map<String, Object> reqBody = new LinkedHashMap<>();
        reqBody.put("scene", scene);               // ≤ 32 chars, ASCII-safe
        if (forceHomepage) {
            // Don’t validate the page; route programmatically by scene in app.js
            reqBody.put("check_path", false);
            // Optional: open trial/release version explicitly
            // reqBody.put("env_version", "release"); // or "trial" / "develop"
        } else {
            // Ensure this page exists in your uploaded code package
            reqBody.put("page", "pages/signup/signup");
        }
        reqBody.put("width", 430);
        reqBody.put("auto_color", false);
        reqBody.put("is_hyaline", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.IMAGE_PNG, MediaType.ALL));
        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(reqBody, headers);

        try {
            log.info("📤 POST {}  body={}", url, reqBody);
            ResponseEntity<byte[]> resp = restTemplate.exchange(url, HttpMethod.POST, httpEntity, byte[].class);
            MediaType ct = resp.getHeaders().getContentType();
            byte[] body = resp.getBody();
            log.info("📥 status={} ct={}", resp.getStatusCode(), ct);

            // Error if non-2xx, empty body, or JSON/text content
            if (!resp.getStatusCode().is2xxSuccessful() || body == null || body.length == 0 ||
                (ct != null && (ct.includes(MediaType.APPLICATION_JSON) || ct.includes(MediaType.TEXT_PLAIN)))) {
                String err = (body == null ? "" : new String(body));
                log.error("❌ WeChat QR error: status={} ct={} body={}", resp.getStatusCode(), ct, err);
                return generateFallbackQRCode(token);
            }

            // Extra guard: WeChat sometimes returns JSON without proper content-type
            if (body[0] == '{') {
                String err = new String(body);
                log.error("❌ WeChat QR JSON error payload: {}", err);
                return generateFallbackQRCode(token);
            }

            String base64 = Base64.getEncoder().encodeToString(body);
            String dataUrl = "data:image/png;base64," + base64;
            log.info("✅ QR generated, bytes={}", body.length);
            return dataUrl;

        } catch (HttpClientErrorException e) {
            log.error("❌ HTTP error: {} - {}", e.getStatusCode(), e.getMessage());
            log.error("Body: {}", e.getResponseBodyAsString());
            return generateFallbackQRCode(token);
        } catch (Exception e) {
            log.error("❌ Exception calling WeChat QR API", e);
            return generateFallbackQRCode(token);
        }
    }

    /** ---- Access token (cached) ---- */

    /**
     * Get WeChat access token (cached; refresh 5 min early).
     */
    public String getAccessToken() throws Exception {
        if (tokenCache.isValid()) return tokenCache.token;

        if (appSecret == null || appSecret.isEmpty()) {
            throw new IllegalStateException("WeChat Mini Program AppSecret not configured. Set MINI_APP_SECRET env var.");
        }

        String url = "https://api.weixin.qq.com/cgi-bin/token"
                + "?grant_type=client_credential"
                + "&appid=" + appId
                + "&secret=" + appSecret;

        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Token HTTP " + resp.getStatusCode());
            }
            Map<?, ?> map = objectMapper.readValue(resp.getBody(), Map.class);
            if (map.get("access_token") == null) {
                throw new RuntimeException("Token error: " + resp.getBody());
            }
            String token = String.valueOf(map.get("access_token"));
            int expiresIn = (map.get("expires_in") instanceof Number)
                    ? ((Number) map.get("expires_in")).intValue()
                    : 7200;

            tokenCache.token = token;
            tokenCache.expiresAtMillis = System.currentTimeMillis() + Math.max(0, (expiresIn - 300)) * 1000L;

            log.info("✅ Access token obtained. expiresIn={}s", expiresIn);
            return token;
        } catch (Exception e) {
            log.error("❌ Failed to fetch access_token", e);
            throw e;
        }
    }

    /** ---- Helpers ---- */

    /**
     * Build a 32-char-safe scene value (ASCII only).
     */
    private String createScene(String token) {
        String shortToken = token == null ? "" : token.replace("group_", "g");
        shortToken = shortToken.replaceAll("[^A-Za-z0-9_-]", "");
        if (shortToken.length() > 30) shortToken = shortToken.substring(0, 30);
        String scene = "t_" + shortToken; // keep format simple, avoid '=' to dodge WAFs
        log.info("📏 Scene='{}' (len={})", scene, scene.length());
        return scene;
    }

    /**
     * Fallback QR (public service) if WeChat fails.
     */
    private String generateFallbackQRCode(String token) {
        log.warn("⚠️ Using fallback QR code generation");
        String path = "pages/signup/signup?token=" + (token == null ? "" : token);
        String encoded = URLEncoder.encode(path, StandardCharsets.UTF_8);
        return "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + encoded;
    }

    public boolean isConfigured() {
        return appSecret != null && !appSecret.isEmpty();
    }

    public Map<String, Object> getConfigStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("appId", appId);
        m.put("secretConfigured", appSecret != null && !appSecret.isEmpty());
        m.put("accessTokenCached", tokenCache.isValid());
        return m;
    }
}