package com.example.demo.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.service-account-key}")
    private String serviceAccountKeyPath;

    @Value("${firebase.storage.bucket}")
    private String storageBucket;

    @PostConstruct
    public void init() throws IOException {
        String credentialsJson = System.getenv("GOOGLE_APPLICATION_CREDENTIALS_JSON");
        if (credentialsJson == null) {
            throw new IllegalStateException("Missing GOOGLE_APPLICATION_CREDENTIALS_JSON env variable");
        }
        GoogleCredentials credentials = GoogleCredentials.fromStream(
            new java.io.ByteArrayInputStream(credentialsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );
        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .setStorageBucket(storageBucket)
            .build();
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }
    }
}
