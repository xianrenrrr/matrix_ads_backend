package com.example.demo.dao;

import com.example.demo.model.User;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.concurrent.ExecutionException;

@Repository
public class UserDaoImpl implements UserDao {
    @Autowired
    private Firestore db;


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
    public User findByEmail(String email) {
        try {
            CollectionReference usersRef = db.collection("users");
            Query query = usersRef.whereEqualTo("email", email).limit(1);
            ApiFuture<QuerySnapshot> querySnapshot = query.get();
            for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
                return document.toObject(User.class);
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch user by email", e);
        }
    }

    @Override
    public User findByEmailAndRole(String email, String role) {
        try {
            CollectionReference usersRef = db.collection("users");
            Query query = usersRef.whereEqualTo("email", email).whereEqualTo("role", role).limit(1);
            ApiFuture<QuerySnapshot> querySnapshot = query.get();
            for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
                return document.toObject(User.class);
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch user by email and role", e);
        }
    }

    @Override
    public User findById(String id) {
        try {
            DocumentReference docRef = db.collection("users").document(id);
            ApiFuture<DocumentSnapshot> documentSnapshot = docRef.get();
            DocumentSnapshot document = documentSnapshot.get();
            if (document.exists()) {
                return document.toObject(User.class);
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch user by id", e);
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

    @Override
    public void addCreatedTemplate(String userId, String templateId) {
        try {
            DocumentReference userRef = db.collection("users").document(userId);
            userRef.update("created_Templates." + templateId, true).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to add created template", e);
        }
    }

    @Override
    public void removeCreatedTemplate(String userId, String templateId) {
        try {
            DocumentReference userRef = db.collection("users").document(userId);
            userRef.update("created_Templates." + templateId, FieldValue.delete()).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove created template", e);
        }
    }

    @Override
    public java.util.Map<String, Boolean> getCreatedTemplates(String userId) {
        try {
            DocumentReference userRef = db.collection("users").document(userId);
            DocumentSnapshot userSnap = userRef.get().get();
            if (userSnap.exists() && userSnap.contains("created_Templates")) {
                Object raw = userSnap.get("created_Templates");
                if (raw instanceof java.util.Map<?, ?> mapRaw) {
                    java.util.Map<String, Boolean> result = new java.util.HashMap<>();
                    for (var entry : mapRaw.entrySet()) {
                        if (entry.getKey() instanceof String key && entry.getValue() instanceof Boolean value) {
                            result.put(key, value);
                        }
                    }
                    return result;
                }
            }
            return new java.util.HashMap<>();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get created templates", e);
        }
    }
}

