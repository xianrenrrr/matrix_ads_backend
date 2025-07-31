package com.example.demo.util;

import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Component
public class FirebaseCredentialsUtil {
    
    @Value("${firebase.service-account-key}")
    private String serviceAccountKeyPath;
    
    /**
     * Gets Firebase credentials - uses environment variable in production, file in development
     */
    public GoogleCredentials getCredentials() throws IOException {
        // First try to get credentials from environment variable (for production/Render)
        String credentialsJson = System.getenv("GOOGLE_APPLICATION_CREDENTIALS_JSON");
        
        if (credentialsJson != null && !credentialsJson.isEmpty()) {
            return GoogleCredentials.fromStream(
                new ByteArrayInputStream(credentialsJson.getBytes())
            );
        }
        
        // Fallback to file-based credentials (for local development)
        if (serviceAccountKeyPath != null && Files.exists(Paths.get(serviceAccountKeyPath))) {
            return GoogleCredentials.fromStream(
                new FileInputStream(serviceAccountKeyPath)
            );
        }
        
        // Final fallback - try default credentials
        System.out.println("Using default Firebase credentials");
        return GoogleCredentials.getApplicationDefault();
    }
}