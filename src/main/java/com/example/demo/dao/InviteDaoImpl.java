package com.example.demo.dao;

import com.example.demo.model.Invite;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Repository
public class InviteDaoImpl implements InviteDao {
    
    @Autowired
    private Firestore db;

    private static final String COLLECTION_NAME = "invites";

    @Override
    public void save(Invite invite) {
        try {
            CollectionReference invitesRef = db.collection(COLLECTION_NAME);
            if (invite.getId() == null || invite.getId().isEmpty()) {
                // Auto-generate ID for new invites
                DocumentReference docRef = invitesRef.document();
                invite.setId(docRef.getId());
            }
            ApiFuture<WriteResult> result = invitesRef.document(invite.getId()).set(invite);
            result.get(); // Wait for completion
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to save invite", e);
        }
    }

    @Override
    public void update(Invite invite) {
        try {
            if (invite.getId() == null || invite.getId().isEmpty()) {
                throw new IllegalArgumentException("Invite ID cannot be null or empty for update");
            }
            CollectionReference invitesRef = db.collection(COLLECTION_NAME);
            ApiFuture<WriteResult> result = invitesRef.document(invite.getId()).set(invite);
            result.get(); // Wait for completion
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to update invite", e);
        }
    }

    @Override
    public Invite findByToken(String token) {
        try {
            CollectionReference invitesRef = db.collection(COLLECTION_NAME);
            Query query = invitesRef.whereEqualTo("token", token).limit(1);
            ApiFuture<QuerySnapshot> querySnapshot = query.get();
            for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
                return document.toObject(Invite.class);
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch invite by token", e);
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
            throw new RuntimeException("Failed to fetch invite by ID", e);
        }
    }

    @Override
    public List<Invite> findByManagerId(String managerId) {
        try {
            CollectionReference invitesRef = db.collection(COLLECTION_NAME);
            Query query = invitesRef.whereEqualTo("managerId", managerId);
            ApiFuture<QuerySnapshot> querySnapshot = query.get();
            
            List<Invite> invites = new ArrayList<>();
            for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
                invites.add(document.toObject(Invite.class));
            }
            return invites;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch invites by manager ID", e);
        }
    }


    @Override
    public List<Invite> findByStatus(String status) {
        try {
            CollectionReference invitesRef = db.collection(COLLECTION_NAME);
            Query query = invitesRef.whereEqualTo("status", status);
            ApiFuture<QuerySnapshot> querySnapshot = query.get();
            
            List<Invite> invites = new ArrayList<>();
            for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
                invites.add(document.toObject(Invite.class));
            }
            return invites;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch invites by status", e);
        }
    }

    @Override
    public void delete(String id) {
        try {
            ApiFuture<WriteResult> writeResult = db.collection(COLLECTION_NAME).document(id).delete();
            writeResult.get(); // Wait for completion
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to delete invite", e);
        }
    }

    @Override
    public void updateStatus(String id, String status) {
        try {
            DocumentReference docRef = db.collection(COLLECTION_NAME).document(id);
            ApiFuture<WriteResult> result = docRef.update("status", status);
            result.get(); // Wait for completion
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to update invite status", e);
        }
    }
}