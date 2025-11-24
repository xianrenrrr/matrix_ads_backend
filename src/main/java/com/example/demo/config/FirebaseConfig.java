package com.example.demo.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.File;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.service-account-key}")
    private String serviceAccountKeyPath;
    
    @Value("${firebase.enabled:true}")
    private boolean firebaseEnabled;

    @Bean(name = "firebaseApp")
    public FirebaseApp initializeFirebase() throws IOException {
        System.out.println("=== Initializing Firebase (Firestore only) ===");
        System.out.println("Firebase enabled: " + firebaseEnabled);
        
        if (!firebaseEnabled) {
            System.out.println("Firebase is disabled - running in development mode");
            return null;
        }
        
        if (!FirebaseApp.getApps().isEmpty()) {
            System.out.println("Firebase app already exists, returning existing instance");
            return FirebaseApp.getInstance();
        }
        
        GoogleCredentials credentials = null;
        
        // Priority 1: Try GOOGLE_APPLICATION_CREDENTIALS file path (Render Secret Files - RECOMMENDED)
        String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credentialsPath != null && !credentialsPath.trim().isEmpty()) {
            File credFile = new File(credentialsPath);
            if (credFile.exists()) {
                System.out.println("✅ Found GOOGLE_APPLICATION_CREDENTIALS file: " + credentialsPath);
                try {
                    credentials = GoogleCredentials.fromStream(new FileInputStream(credFile));
                    System.out.println("✅ Successfully loaded Firebase credentials from file");
                } catch (Exception e) {
                    System.err.println("❌ Failed to load credentials from file: " + e.getMessage());
                    throw e;
                }
            } else {
                System.err.println("⚠️ GOOGLE_APPLICATION_CREDENTIALS points to non-existent file: " + credentialsPath);
            }
        }
        
        // Priority 2: Try GOOGLE_APPLICATION_CREDENTIALS_JSON environment variable (fallback)
        if (credentials == null) {
            String credentialsJson = System.getenv("GOOGLE_APPLICATION_CREDENTIALS_JSON");
            if (credentialsJson != null && !credentialsJson.trim().isEmpty()) {
                System.out.println("Found GOOGLE_APPLICATION_CREDENTIALS_JSON environment variable");
                System.out.println("JSON length: " + credentialsJson.length());
                try {
                    credentials = GoogleCredentials.fromStream(
                        new java.io.ByteArrayInputStream(credentialsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    );
                    System.out.println("✅ Successfully loaded Firebase credentials from JSON environment variable");
                } catch (Exception e) {
                    System.err.println("❌ Failed to parse GOOGLE_APPLICATION_CREDENTIALS_JSON: " + e.getMessage());
                    System.err.println("First 100 chars: " + credentialsJson.substring(0, Math.min(100, credentialsJson.length())));
                    throw e;
                }
            }
        }
        
        // Priority 3: Try local service account file (for development)
        if (credentials == null && serviceAccountKeyPath != null) {
            File localFile = new File(serviceAccountKeyPath);
            if (localFile.exists()) {
                System.out.println("Found local service account file: " + serviceAccountKeyPath);
                credentials = GoogleCredentials.fromStream(new FileInputStream(localFile));
                System.out.println("✅ Successfully loaded Firebase credentials from local file");
            }
        }
        
        // If still no credentials found
        if (credentials == null) {
            System.err.println("❌ ERROR: No Firebase credentials found!");
            System.err.println("");
            System.err.println("For Render deployment, use Secret Files (RECOMMENDED):");
            System.err.println("1. In Render Dashboard → Environment → Add Secret File");
            System.err.println("2. Filename: /etc/secrets/firebase-credentials.json");
            System.err.println("3. Contents: Paste your Firebase Admin SDK JSON");
            System.err.println("4. Add environment variable:");
            System.err.println("   GOOGLE_APPLICATION_CREDENTIALS=/etc/secrets/firebase-credentials.json");
            System.err.println("");
            System.err.println("Alternative: Use environment variable (less secure):");
            System.err.println("   GOOGLE_APPLICATION_CREDENTIALS_JSON=<minified-json>");
            System.err.println("");
            System.err.println("For local development:");
            System.err.println("   Set firebase.enabled=false in application.properties");
            return null;
        }
        
        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .build();
        
        FirebaseApp app = FirebaseApp.initializeApp(options);
        System.out.println("✅ Firebase initialized successfully!");
        return app;
    }
}
