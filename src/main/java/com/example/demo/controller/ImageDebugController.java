package com.example.demo.controller;

import com.example.demo.util.FirebaseCredentialsUtil;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@CrossOrigin(origins = {"http://localhost:4040", "https://matrix-ads-frontend.onrender.com"})
public class ImageDebugController {

    @Autowired
    private FirebaseCredentialsUtil firebaseCredentialsUtil;

    @Value("${firebase.storage.bucket}")
    private String bucketName;

    /**
     * Check if a GCS object exists and return its metadata
     * GET /api/debug/gcs-object-exists?path=keyframes/abc123.jpg
     */
    @GetMapping("/gcs-object-exists")
    public ResponseEntity<Map<String, Object>> checkGcsObjectExists(@RequestParam String path) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get Firebase credentials
            GoogleCredentials credentials = firebaseCredentialsUtil.getCredentials();
            
            // Create storage client
            Storage storage = StorageOptions.newBuilder()
                .setCredentials(credentials)
                .build()
                .getService();
                
            // Check if object exists
            Blob blob = storage.get(bucketName, path);
            
            if (blob != null && blob.exists()) {
                response.put("exists", true);
                response.put("bucket", bucketName);
                response.put("path", path);
                response.put("size", blob.getSize());
                response.put("contentType", blob.getContentType());
                response.put("timeCreated", blob.getCreateTime());
                response.put("updated", blob.getUpdateTime());
                response.put("md5Hash", blob.getMd5());
                
                System.out.printf("✅ GCS Object exists: gs://%s/%s%n", bucketName, path);
                System.out.printf("   Size: %d bytes%n", blob.getSize());
                System.out.printf("   Content-Type: %s%n", blob.getContentType());
                System.out.printf("   Created: %s%n", blob.getCreateTime());
            } else {
                response.put("exists", false);
                response.put("bucket", bucketName);
                response.put("path", path);
                response.put("error", "Object not found in GCS");
                
                System.out.printf("❌ GCS Object not found: gs://%s/%s%n", bucketName, path);
            }
            
        } catch (Exception e) {
            response.put("exists", false);
            response.put("bucket", bucketName);
            response.put("path", path);
            response.put("error", e.getMessage());
            
            System.err.printf("❌ Error checking GCS object gs://%s/%s: %s%n", bucketName, path, e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Parse a signed URL and extract its components
     * POST /api/debug/parse-signed-url
     */
    @PostMapping("/parse-signed-url")
    public ResponseEntity<Map<String, Object>> parseSignedUrl(@RequestBody Map<String, String> request) {
        String url = request.get("url");
        Map<String, Object> response = new HashMap<>();
        
        try {
            response.put("originalUrl", url);
            
            if (url.contains("X-Goog-Date=")) {
                // Extract signed URL components
                String[] parts = url.split("\\?", 2);
                if (parts.length == 2) {
                    response.put("basePath", parts[0]);
                    
                    // Parse query parameters
                    String[] params = parts[1].split("&");
                    Map<String, String> queryParams = new HashMap<>();
                    
                    for (String param : params) {
                        String[] kv = param.split("=", 2);
                        if (kv.length == 2) {
                            queryParams.put(kv[0], kv[1]);
                        }
                    }
                    
                    response.put("queryParams", queryParams);
                    
                    // Parse date and expiry
                    if (queryParams.containsKey("X-Goog-Date") && queryParams.containsKey("X-Goog-Expires")) {
                        String dateStr = queryParams.get("X-Goog-Date");
                        String expiresStr = queryParams.get("X-Goog-Expires");
                        
                        // Parse ISO date
                        java.time.Instant signedDate = java.time.Instant.parse(
                            dateStr.replaceAll("(\\d{4})(\\d{2})(\\d{2})T(\\d{2})(\\d{2})(\\d{2})Z", "$1-$2-$3T$4:$5:$6Z")
                        );
                        java.time.Instant expiryDate = signedDate.plusSeconds(Long.parseLong(expiresStr));
                        boolean isExpired = java.time.Instant.now().isAfter(expiryDate);
                        
                        response.put("signedAt", signedDate.toString());
                        response.put("expiresAt", expiryDate.toString());
                        response.put("expiresInSeconds", expiresStr);
                        response.put("isExpired", isExpired);
                        response.put("minutesUntilExpiry", 
                            java.time.Duration.between(java.time.Instant.now(), expiryDate).toMinutes());
                        
                        System.out.printf("🔍 Parsed signed URL:%n");
                        System.out.printf("   Signed at: %s%n", signedDate);
                        System.out.printf("   Expires at: %s%n", expiryDate);
                        System.out.printf("   Status: %s%n", isExpired ? "🔴 EXPIRED" : "🟢 VALID");
                    }
                }
            } else {
                response.put("type", "permanent_url");
                response.put("isSigned", false);
            }
            
        } catch (Exception e) {
            response.put("error", e.getMessage());
            System.err.printf("❌ Error parsing signed URL: %s%n", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
}