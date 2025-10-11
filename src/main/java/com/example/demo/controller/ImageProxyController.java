package com.example.demo.controller;

import com.example.demo.service.FirebaseStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping({"/api/images", "/images"})
@CrossOrigin(origins = {"http://localhost:4040", "https://matrix-ads-frontend.onrender.com"})
public class ImageProxyController {

    private static final Logger log = LoggerFactory.getLogger(ImageProxyController.class);

    @Value("${firebase.storage.bucket}")
    private String bucketName;

    @Autowired(required = false)
    private FirebaseStorageService firebaseStorageService;

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/proxy")
    public ResponseEntity<byte[]> proxy(@RequestParam("path") String path) {
        if (path == null || path.isBlank()) {
            log.warn("[images/proxy] Missing path parameter");
            return ResponseEntity.badRequest().build();
        }

        // Decode the incoming path
        String decoded = decode(path);

        // First attempt: use provided path as full URL or build GCS URL
        String targetUrl = normalizeToUrl(decoded);
        ResponseEntity<byte[]> resp = fetch(targetUrl);
        if (resp.getStatusCode().is2xxSuccessful()) {
            return passThrough(resp);
        }

        // Decode to object path for fallback attempts
        String objectPath = extractObjectPath(decoded);
        String unsignedUrl = "https://storage.googleapis.com/" + bucketName + "/" + objectPath;

        // Fallback 1: try unsigned public URL
        ResponseEntity<byte[]> unsignedResp = fetch(unsignedUrl);
        if (unsignedResp.getStatusCode().is2xxSuccessful()) {
            return passThrough(unsignedResp);
        }

        // Fallback 2: If expired/unauthorized and Firebase available, mint fresh signed URL
        if ((resp.getStatusCode() == HttpStatus.FORBIDDEN || resp.getStatusCode() == HttpStatus.BAD_REQUEST
                || resp.getStatusCode() == HttpStatus.UNAUTHORIZED
                || unsignedResp.getStatusCode() == HttpStatus.FORBIDDEN || unsignedResp.getStatusCode() == HttpStatus.UNAUTHORIZED)
                && firebaseStorageService != null) {
            try {
                String freshSigned = firebaseStorageService.generateSignedUrl(unsignedUrl);
                ResponseEntity<byte[]> retry = fetch(freshSigned);
                if (retry.getStatusCode().is2xxSuccessful()) {
                    return passThrough(retry);
                }
                return ResponseEntity.status(retry.getStatusCode()).build();
            } catch (Exception e) {
                log.error("[images/proxy] Failed to refresh signed URL: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }
        }

        return ResponseEntity.status(resp.getStatusCode()).build();
    }

    private ResponseEntity<byte[]> fetch(String url) {
        try {
            var entity = new org.springframework.http.HttpEntity<Void>(new HttpHeaders());
            return restTemplate.exchange(new URI(url), HttpMethod.GET, entity, byte[].class);
        } catch (URISyntaxException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            return new ResponseEntity<>(e.getResponseBodyAsByteArray(), e.getResponseHeaders(), e.getStatusCode());
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            return new ResponseEntity<>(e.getResponseBodyAsByteArray(), e.getResponseHeaders(), e.getStatusCode());
        } catch (Exception e) {
            log.error("[images/proxy] Fetch error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private ResponseEntity<byte[]> passThrough(ResponseEntity<byte[]> resp) {
        HttpHeaders headers = new HttpHeaders();
        MediaType ct = resp.getHeaders().getContentType();
        if (ct != null) headers.setContentType(ct);
        // Avoid caching signed URLs client-side; they expire quickly
        headers.set("Cache-Control", "no-store, max-age=0");
        return new ResponseEntity<>(resp.getBody(), headers, HttpStatus.OK);
    }

    private String normalizeToUrl(String pathOrUrl) {
        String p = pathOrUrl.trim();
        if (p.startsWith("http://") || p.startsWith("https://")) {
            return p;
        }
        // Support form: "keyframes/abc.jpg?X-Goog-..." (decoded already)
        String pathOnly = p;
        String query = null;
        int q = p.indexOf('?');
        if (q >= 0) {
            pathOnly = p.substring(0, q);
            query = p.substring(q + 1);
        }
        if (pathOnly.startsWith("/")) pathOnly = pathOnly.substring(1);
        String base = "https://storage.googleapis.com/" + bucketName + "/" + pathOnly;
        return (query != null && !query.isBlank()) ? base + "?" + query : base;
    }

    private String extractObjectPath(String pathOrUrl) {
        String p = pathOrUrl;
        try {
            if (p.startsWith("http://") || p.startsWith("https://")) {
                // Example: https://storage.googleapis.com/<bucket>/<object>?signed
                URI uri = new URI(p);
                String rawPath = uri.getPath(); // /<bucket>/<object>
                if (rawPath != null && rawPath.startsWith("/")) rawPath = rawPath.substring(1);
                if (rawPath != null && rawPath.startsWith(bucketName + "/")) {
                    return rawPath.substring(bucketName.length() + 1);
                }
                // If not the expected host, fall through
            }
        } catch (Exception ignored) {}
        // Strip query if present
        int q = p.indexOf('?');
        if (q >= 0) {
            return p.substring(0, q);
        }
        return p;
    }

    private String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private String redact(String url) {
        if (url == null) return null;
        // hide signature to avoid leaking secrets in logs
        return url.replaceAll("(X-Goog-Signature=)[A-Fa-f0-9]+", "$1<redacted>");
    }

    private String safeTrim(String s) {
        if (s == null) return null;
        return s.length() > 256 ? s.substring(0, 256) + "..." : s;
    }
}
