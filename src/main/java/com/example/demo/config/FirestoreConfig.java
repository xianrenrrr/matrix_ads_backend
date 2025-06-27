package com.example.demo.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.File;

@Configuration
public class FirestoreConfig {
    
    @Value("${firebase.enabled:true}")
    private boolean firebaseEnabled;
    
    @Bean
    public Firestore getFirestore() throws IOException {
        if (!firebaseEnabled) {
            System.out.println("Firebase is disabled - running in development mode");
            return null; // Return null for development mode
        }
        
        if (FirebaseApp.getApps().isEmpty()) {
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
                } else {
                    // Check for serviceAccountKey.json in project root
                    File serviceAccountFile = new File("serviceAccountKey.json");
                    if (serviceAccountFile.exists()) {
                        credentials = GoogleCredentials.fromStream(new FileInputStream(serviceAccountFile));
                    } else {
                        System.out.println("WARNING: No Firebase credentials found. Running without Firebase.");
                        System.out.println("To fix this, either:");
                        System.out.println("1. Set GOOGLE_APPLICATION_CREDENTIALS_JSON environment variable");
                        System.out.println("2. Set GOOGLE_APPLICATION_CREDENTIALS to point to service account file");
                        System.out.println("3. Place serviceAccountKey.json in project root");
                        System.out.println("4. Set firebase.enabled=false in application.properties for development");
                        return null;
                    }
                }
            }
            
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();
            FirebaseApp.initializeApp(options);
        }
        return FirestoreClient.getFirestore();
    }
}
