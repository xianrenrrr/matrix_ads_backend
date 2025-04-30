package com.example.demo.dao;

import com.example.demo.model.User;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

@Repository
public class UserDaoImpl implements UserDao {
    private Firestore db;

    @PostConstruct
    public void connectToFirestore() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FileInputStream serviceAccount = new FileInputStream("./serviceAccountKey.json");
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
                FirebaseApp.initializeApp(options);
            }
            db = FirestoreClient.getFirestore();
        } catch (IOException e) {
            throw new RuntimeException("Failed to connect to Firestore", e);
        }
    }

    @Override
    public User findByUsername(String username) {
        try {
            CollectionReference usersRef = db.collection("users");
            Query query = usersRef.whereEqualTo("username", username).limit(1);
            ApiFuture<QuerySnapshot> querySnapshot = query.get();
            for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
                return document.toObject(User.class);
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch user by username", e);
        }
    }

    @Override
    public void save(User user) {
        try {
            DocumentReference docRef = db.collection("users").document(user.getId());
            ApiFuture<WriteResult> result = docRef.set(user);
            result.get(); // Wait for write to complete
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to save user", e);
        }
    }
}

