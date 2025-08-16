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
        
        return FirestoreClient.getFirestore();
    }
}
