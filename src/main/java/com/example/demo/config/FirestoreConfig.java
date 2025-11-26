package com.example.demo.config;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.io.IOException;

@Configuration
public class FirestoreConfig {
    
    @Value("${firebase.enabled:true}")
    private boolean firebaseEnabled;
    
    @Value("${firebase.service-account-key}")
    private String serviceAccountKeyPath;
    
    @Value("${firestore.database-id:xpectra1}")
    private String databaseId;
    
    @Bean
    @DependsOn("firebaseApp")
    public Firestore getFirestore() throws IOException {
        if (!firebaseEnabled) {
            System.out.println("Firebase is disabled - running in development mode");
            return null; // Return null for development mode
        }
        
        // Firebase should already be initialized by FirebaseConfig
        if (FirebaseApp.getApps().isEmpty()) {
            throw new IllegalStateException("Firebase app not initialized. Check FirebaseConfig.");
        }
        
        // Use specified database ID (default: xpectra1 in asia-southeast1)
        // To use default database, set firestore.database-id=(default)
        System.out.println("=== Connecting to Firestore ===");
        System.out.println("Database ID: " + databaseId);
        
        if ("(default)".equals(databaseId) || databaseId == null || databaseId.isEmpty()) {
            System.out.println("Using default Firestore database");
            return FirestoreClient.getFirestore();
        } else {
            System.out.println("Using named Firestore database: " + databaseId);
            return FirestoreClient.getFirestore(FirebaseApp.getInstance(), databaseId);
        }
    }
}
