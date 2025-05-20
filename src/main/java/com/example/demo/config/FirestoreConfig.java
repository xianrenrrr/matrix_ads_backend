package com.example.demo.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class FirestoreConfig {
    @Bean
    public Firestore getFirestore() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            String credentialsJson = System.getenv("GOOGLE_APPLICATION_CREDENTIALS_JSON");
            if (credentialsJson == null) {
                throw new IllegalStateException("Missing GOOGLE_APPLICATION_CREDENTIALS_JSON env variable");
            }
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                new java.io.ByteArrayInputStream(credentialsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();
            FirebaseApp.initializeApp(options);
        }
        return FirestoreClient.getFirestore();
    }
}
