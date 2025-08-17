package com.example.demo.dao;

import com.example.demo.model.Invite;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Repository
public class GroupDaoImpl implements GroupDao {
    
    @Autowired
    private Firestore db;

    private static final String COLLECTION_NAME = "groups";

    @Override
    public void save(Invite invite) {
        try {
            CollectionReference groupsRef = db.collection(COLLECTION_NAME);
            if (invite.getId() == null || invite.getId().isEmpty()) {
                // Auto-generate ID for new groups
                DocumentReference docRef = groupsRef.document();
                invite.setId(docRef.getId());
            }
            ApiFuture<WriteResult> result = groupsRef.document(invite.getId()).set(invite);
            result.get(); // Wait for completion
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to save group", e);
        }
    }

    @Override
    public void update(Invite invite) {
        try {
            if (invite.getId() == null || invite.getId().isEmpty()) {
                throw new IllegalArgumentException("Group ID cannot be null or empty for update");
            }
            CollectionReference groupsRef = db.collection(COLLECTION_NAME);
            ApiFuture<WriteResult> result = groupsRef.document(invite.getId()).set(invite);
            result.get(); // Wait for completion
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to update group", e);
        }
    }

    @Override
    public Invite findByToken(String token) {
        try {
            CollectionReference groupsRef = db.collection(COLLECTION_NAME);
            Query query = groupsRef.whereEqualTo("token", token).limit(1);
            ApiFuture<QuerySnapshot> querySnapshot = query.get();
            for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
                return document.toObject(Invite.class);
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch group by token", e);
        }
    }

    @Override
    public Invite findById(String id) {
        try {
            DocumentReference docRef = db.collection(COLLECTION_NAME).document(id);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot document = future.get();
            if (document.exists()) {
                return document.toObject(Invite.class);
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch group by ID", e);
        }
    }

    @Override
    public List<Invite> findByManagerId(String managerId) {
        try {
            CollectionReference groupsRef = db.collection(COLLECTION_NAME);
            Query query = groupsRef.whereEqualTo("managerId", managerId);
            ApiFuture<QuerySnapshot> querySnapshot = query.get();
            
            List<Invite> groups = new ArrayList<>();
            for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
                groups.add(document.toObject(Invite.class));
            }
            return groups;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch groups by manager ID", e);
        }
    }


    @Override
    public List<Invite> findByStatus(String status) {
        try {
            CollectionReference groupsRef = db.collection(COLLECTION_NAME);
            Query query = groupsRef.whereEqualTo("status", status);
            ApiFuture<QuerySnapshot> querySnapshot = query.get();
            
            List<Invite> groups = new ArrayList<>();
            for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
                groups.add(document.toObject(Invite.class));
            }
            return groups;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch groups by status", e);
        }
    }

    @Override
    public void delete(String id) {
        try {
            ApiFuture<WriteResult> writeResult = db.collection(COLLECTION_NAME).document(id).delete();
            writeResult.get(); // Wait for completion
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to delete group", e);
        }
    }

    @Override
    public void updateStatus(String id, String status) {
        try {
            DocumentReference docRef = db.collection(COLLECTION_NAME).document(id);
            ApiFuture<WriteResult> result = docRef.update("status", status);
            result.get(); // Wait for completion
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to update group status", e);
        }
    }
    
    @Override
    public String getUserGroupId(String userId) {
        try {
            Query query = db.collection(COLLECTION_NAME)
                .whereEqualTo("recipientEmail", userId)
                .whereEqualTo("status", "accepted")
                .limit(1);
                
            ApiFuture<QuerySnapshot> querySnapshot = query.get();
            QuerySnapshot snapshot = querySnapshot.get();
            
            if (!snapshot.isEmpty()) {
                return snapshot.getDocuments().get(0).getString("groupId");
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to get user group ID", e);
        }
    }
    
    @Override
    public void addTemplateToGroup(String groupId, String templateId) {
        try {
            DocumentReference groupRef = db.collection(COLLECTION_NAME).document(groupId);
            ApiFuture<WriteResult> result = groupRef.update("assignedTemplates", FieldValue.arrayUnion(templateId));
            result.get(); // Wait for completion
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to add template to group", e);
        }
    }
    
    @Override
    public void removeTemplateFromGroup(String groupId, String templateId) {
        try {
            DocumentReference groupRef = db.collection(COLLECTION_NAME).document(groupId);
            ApiFuture<WriteResult> result = groupRef.update("assignedTemplates", FieldValue.arrayRemove(templateId));
            result.get(); // Wait for completion
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to remove template from group", e);
        }
    }
}