package com.example.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class WeChatMiniProgramService {

    private static final Logger log = LoggerFactory.getLogger(WeChatMiniProgramService.class);

    @Value("${wechat.miniprogram.appid:wx351e2b055747fa78}")
    private String appId;

    @Value("${wechat.miniprogram.secret:${MINI_APP_SECRET:}}")
    private String appSecret;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static class AccessTokenCache {
        String token;
        long expiresAtMillis;
        boolean isValid() { return token != null && System.currentTimeMillis() < expiresAtMillis; }
    }
    private final AccessTokenCache tokenCache = new AccessTokenCache();

    public WeChatMiniProgramService() {
        // Apache HttpClient 4.x request factory (sets Content-Length, avoids chunked)
        CloseableHttpClient httpClient = HttpClients.custom().disableAuthCaching().disableCookieManagement().build();
        HttpComponentsClientHttpRequestFactory hc = new HttpComponentsClientHttpRequestFactory(httpClient);
        hc.setConnectTimeout(10_000);
        hc.setReadTimeout(20_000);

        // Buffering wrapper lets you read/inspect body if you ever add logging interceptors
        this.restTemplate = new RestTemplate(new BufferingClientHttpRequestFactory(hc));
    }

    /** Generate a Mini Program QR Code (returns data URL on success, public QR URL on failure). */
    public String generateMiniProgramQRCode(String token, boolean forceHomepage) throws Exception {
        log.info("🔍 Start WeChat QR generation, token={}", token);

        String accessToken = getAccessToken();
        String url = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + accessToken;

        String scene = createScene(token);

        Map<String, Object> reqBody = new LinkedHashMap<>();
        reqBody.put("scene", scene);                 // ≤32 ASCII
        if (forceHomepage) {
            reqBody.put("check_path", false);        // don’t validate page
            // reqBody.put("env_version", "release"); // or "trial"/"develop"
        } else {
            reqBody.put("page", "pages/signup/signup"); // must exist in uploaded code
        }
        reqBody.put("width", 430);
        reqBody.put("auto_color", false);
        reqBody.put("is_hyaline", false);

        // 👉 Serialize to JSON String (most reliable for WeChat gateway)
        String json = objectMapper.writeValueAsString(reqBody);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Arrays.asList(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG, MediaType.ALL));
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0"); // some edges are picky

        HttpEntity<String> entity = new HttpEntity<>(json, headers);

        try {
            log.info("📤 POST {}  body={}", url, json);
            ResponseEntity<byte[]> resp = restTemplate.exchange(url, HttpMethod.POST, entity, byte[].class);
            MediaType ct = resp.getHeaders().getContentType();
            byte[] body = resp.getBody();
            log.info("📥 status={} ct={} len={}", resp.getStatusCode(), ct,
                     resp.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH));

            // Treat non-2xx, empty, or JSON/text bodies as error
            if (!resp.getStatusCode().is2xxSuccessful() || body == null || body.length == 0 ||
                (ct != null && (ct.includes(MediaType.APPLICATION_JSON) || ct.includes(MediaType.TEXT_PLAIN))) ||
                (body.length > 0 && body[0] == '{')) {
                String err = (body == null ? "" : new String(body));
                log.error("❌ WeChat QR error: status={} ct={} body={}", resp.getStatusCode(), ct, err);
                return generateFallbackQRCode(token);
            }

            String base64 = Base64.getEncoder().encodeToString(body);
            return "data:image/png;base64," + base64;

        } catch (HttpClientErrorException e) {
            log.error("❌ HTTP error: {} - {}", e.getStatusCode(), e.getMessage());
            log.error("Body: {}", e.getResponseBodyAsString());
            return generateFallbackQRCode(token);
        } catch (Exception e) {
            log.error("❌ Exception calling WeChat QR API", e);
            return generateFallbackQRCode(token);
        }
    }

    /** Access token (cached; refresh 5 min early). */
    public String getAccessToken() throws Exception {
        if (tokenCache.isValid()) return tokenCache.token;
        if (appSecret == null || appSecret.isEmpty()) {
            throw new IllegalStateException("WeChat Mini Program AppSecret not configured. Set MINI_APP_SECRET env var.");
        }

        String url = "https://api.weixin.qq.com/cgi-bin/token"
                + "?grant_type=client_credential"
                + "&appid=" + appId
                + "&secret=" + appSecret;

        ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new RuntimeException("Token HTTP " + resp.getStatusCode());
        }
        Map body = resp.getBody();
        Object at = body.get("access_token");
        if (at == null) {
            throw new RuntimeException("Token error: " + body);
        }

        String token = String.valueOf(at);
        int expiresIn = body.get("expires_in") instanceof Number
                ? ((Number) body.get("expires_in")).intValue()
                : 7200;

        tokenCache.token = token;
        tokenCache.expiresAtMillis = System.currentTimeMillis() + Math.max(0, (expiresIn - 300)) * 1000L;
        log.info("✅ Access token obtained. expiresIn={}s", expiresIn);
        return token;
    }

    /** Build a 32-char-safe scene value (ASCII only). */
    private String createScene(String token) {
        String shortToken = token == null ? "" : token.replace("group_", "g");
        shortToken = shortToken.replaceAll("[^A-Za-z0-9_-]", "");
        if (shortToken.length() > 30) shortToken = shortToken.substring(0, 30);
        String scene = "t_" + shortToken; // avoid '='
        log.info("📏 Scene='{}' (len={})", scene, scene.length());
        return scene;
    }

    /** Fallback QR (public service) if WeChat fails. */
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