package com.example.demo.dao;

import com.example.demo.model.User;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

@Repository
public class UserDaoImpl implements UserDao {
    @Autowired
    private Firestore db;
    
    private static final String COLLECTION_NAME = "users";


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
    public User findByPhone(String phone) {
        try {
            CollectionReference usersRef = db.collection("users");
            Query query = usersRef.whereEqualTo("phone", phone).limit(1);
            ApiFuture<QuerySnapshot> querySnapshot = query.get();
            for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
                return document.toObject(User.class);
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch user by phone", e);
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

    // Content Creator: manage subscribed_Templates
    @Override
    public void addSubscribedTemplate(String userId, String templateId) {
        try {
            DocumentReference userRef = db.collection(COLLECTION_NAME).document(userId);
            userRef.update("subscribed_Templates." + templateId, true).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to add subscribed template", e);
        }
    }

    @Override
    public void removeSubscribedTemplate(String userId, String templateId) {
        try {
            DocumentReference userRef = db.collection(COLLECTION_NAME).document(userId);
            userRef.update("subscribed_Templates." + templateId, FieldValue.delete()).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove subscribed template", e);
        }
    }

    @Override
    public java.util.Map<String, Boolean> getSubscribedTemplates(String userId) {
        try {
            DocumentSnapshot userDoc = db.collection(COLLECTION_NAME).document(userId).get().get();
            if (userDoc.exists() && userDoc.contains("subscribed_Templates")) {
                Object subscribedTemplatesObj = userDoc.get("subscribed_Templates");
                if (subscribedTemplatesObj instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Boolean> result = (java.util.Map<String, Boolean>) subscribedTemplatesObj;
                    return result;
                }
            }
            return new java.util.HashMap<>();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get subscribed templates", e);
        }
    }
    
    @Override
    public java.util.List<User> findByRole(String role) {
        try {
            Query query = db.collection(COLLECTION_NAME).whereEqualTo("role", role);
            QuerySnapshot querySnapshot = query.get().get();
            
            java.util.List<User> users = new java.util.ArrayList<>();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                User user = doc.toObject(User.class);
                if (user != null) {
                    user.setId(doc.getId());
                    users.add(user);
                }
            }
            return users;
        } catch (Exception e) {
            throw new RuntimeException("Failed to find users by role: " + role, e);
        }
    }
    
    /**
     * Create a new user with plain text password
     * Returns the generated user ID
     */
    public String createUser(User user) {
        try {
            // Generate ID if not set
            if (user.getId() == null || user.getId().isEmpty()) {
                user.setId(UUID.randomUUID().toString());
            }
            
            // Store password as plain text (no encoding)
            // Password is already set in the user object
            
            // Save to Firestore
            DocumentReference docRef = db.collection(COLLECTION_NAME).document(user.getId());
            ApiFuture<WriteResult> result = docRef.set(user);
            result.get(); // Wait for write to complete
            
            return user.getId();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to create user", e);
        }
    }
    
    /**
     * Authenticate user with plain text password comparison
     */
    public User authenticateUser(String username, String password) {
        try {
            User user = findByUsername(username);
            if (user == null) {
                return null;
            }
            
            String storedPassword = user.getPassword();
            
            // Simple plain text password comparison
            if (password.equals(storedPassword)) {
                System.out.println("Authentication: Password match for user: " + username);
                return user;
            }
            
            System.out.println("Authentication: Password mismatch for user: " + username);
            return null;
            
        } catch (Exception e) {
            System.err.println("Authentication error: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Find users created by a specific manager (for employee management)
     */
    public java.util.List<User> findByCreatedBy(String managerId) {
        try {
            Query query = db.collection(COLLECTION_NAME).whereEqualTo("createdBy", managerId);
            QuerySnapshot querySnapshot = query.get().get();
            
            java.util.List<User> employees = new java.util.ArrayList<>();
            for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                User user = document.toObject(User.class);
                if (user != null) {
                    user.setId(document.getId());
                    employees.add(user);
                }
            }
            
            return employees;
        } catch (Exception e) {
            System.err.println("Error finding users by createdBy: " + e.getMessage());
            return new java.util.ArrayList<>();
        }
    }
    
    /**
     * Delete a user by ID
     */
    public void delete(String userId) {
        try {
            DocumentReference docRef = db.collection(COLLECTION_NAME).document(userId);
            ApiFuture<WriteResult> result = docRef.delete();
            result.get(); // Wait for delete to complete
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to delete user", e);
        }
    }
}

