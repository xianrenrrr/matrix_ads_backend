package com.example.demo.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.File;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.service-account-key}")
    private String serviceAccountKeyPath;

    @Value("${firebase.storage.bucket}")
    private String storageBucket;
    
    @Value("${firebase.enabled:true}")
    private boolean firebaseEnabled;

    @PostConstruct
    public void init() throws IOException {
        if (!firebaseEnabled) {
            System.out.println("Firebase Storage is disabled - running in development mode");
            return;
        }
        
        GoogleCredentials credentials = null;
        
        // Try environment variable first (for production)
        String credentialsJson = System.getenv("GOOGLE_APPLICATION_CREDENTIALS_JSON");
        if (credentialsJson != null) {
            credentials = GoogleCredentials.fromStream(
                new java.io.ByteArrayInputStream(credentialsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        } else {
            // Try service account file (for local development)
            String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (credentialsPath != null && new File(credentialsPath).exists()) {
                credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsPath));
            } else if (serviceAccountKeyPath != null && new File(serviceAccountKeyPath).exists()) {
                credentials = GoogleCredentials.fromStream(new FileInputStream(serviceAccountKeyPath));
            } else {
                System.out.println("WARNING: No Firebase Storage credentials found. Running without Firebase Storage.");
                System.out.println("To fix this, either:");
                System.out.println("1. Set GOOGLE_APPLICATION_CREDENTIALS_JSON environment variable");
                System.out.println("2. Set GOOGLE_APPLICATION_CREDENTIALS to point to service account file");
                System.out.println("3. Place serviceAccountKey.json in the specified path");
                System.out.println("4. Set firebase.enabled=false in application.properties for development");
                return;
            }
        }
        
        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .setStorageBucket(storageBucket)
            .build();
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }
    }
}
