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

