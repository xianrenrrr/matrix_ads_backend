package com.example.demo.controller;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.FileInputStream;

@RestController
@RequestMapping("/api/images")
@CrossOrigin(origins = "*")
public class ImageProxyController {
    
    @Value("${firebase.service-account-key}")
    private String serviceAccountKeyPath;

    @Value("${firebase.storage.bucket}")
    private String bucketName;
    
    @GetMapping("/proxy")
    public ResponseEntity<byte[]> proxyImage(@RequestParam String path) {
        try {
            // Create credentials from service account key file
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                new FileInputStream(serviceAccountKeyPath)
            );
            
            // Create storage client with credentials
            Storage storage = StorageOptions.newBuilder()
                .setCredentials(credentials)
                .build()
                .getService();
                
            Blob blob = storage.get(bucketName, path);
            
            if (blob == null || !blob.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            byte[] imageBytes = blob.getContent();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            headers.setCacheControl("public, max-age=31536000"); // Cache for 1 year
            
            return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
            
        } catch (Exception e) {
            System.err.printf("Error proxying image %s: %s%n", path, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}