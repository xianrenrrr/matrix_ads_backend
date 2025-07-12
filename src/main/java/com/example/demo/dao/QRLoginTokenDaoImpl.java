package com.example.demo.dao;

import com.example.demo.model.QRLoginToken;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Repository
public class QRLoginTokenDaoImpl implements QRLoginTokenDao {
    
    @Autowired
    private Firestore db;
    
    private static final String COLLECTION_NAME = "qr_login_tokens";

    @Override
    public void save(QRLoginToken token) {
        try {
            Map<String, Object> tokenData = new HashMap<>();
            tokenData.put("tokenId", token.getTokenId());
            tokenData.put("userId", token.getUserId());
            tokenData.put("token", token.getToken());
            tokenData.put("createdAt", token.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            tokenData.put("expiresAt", token.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            tokenData.put("used", token.isUsed());
            tokenData.put("platform", token.getPlatform());
            
            db.collection(COLLECTION_NAME).document(token.getTokenId()).set(tokenData).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to save QR login token", e);
        }
    }

    @Override
    public Optional<QRLoginToken> findByTokenId(String tokenId) {
        try {
            var document = db.collection(COLLECTION_NAME).document(tokenId).get().get();
            
            if (!document.exists()) {
                return Optional.empty();
            }
            
            return Optional.of(documentToQRLoginToken(document.getData()));
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to find QR login token by tokenId", e);
        }
    }

    @Override
    public Optional<QRLoginToken> findByToken(String token) {
        try {
            QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME)
                .whereEqualTo("token", token)
                .get()
                .get();
            
            if (querySnapshot.isEmpty()) {
                return Optional.empty();
            }
            
            QueryDocumentSnapshot document = querySnapshot.getDocuments().get(0);
            return Optional.of(documentToQRLoginToken(document.getData()));
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to find QR login token by token", e);
        }
    }

    @Override
    public void markAsUsed(String tokenId) {
        try {
            Map<String, Object> updates = new HashMap<>();
            updates.put("used", true);
            
            db.collection(COLLECTION_NAME).document(tokenId).update(updates).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to mark QR login token as used", e);
        }
    }

    @Override
    public void deleteExpiredTokens() {
        try {
            long currentTime = System.currentTimeMillis();
            QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME)
                .whereLessThan("expiresAt", currentTime)
                .get()
                .get();
            
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                document.getReference().delete();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to delete expired QR login tokens", e);
        }
    }

    @Override
    public void deleteByUserId(String userId) {
        try {
            QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME)
                .whereEqualTo("userId", userId)
                .get()
                .get();
            
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                document.getReference().delete();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to delete QR login tokens by userId", e);
        }
    }

    private QRLoginToken documentToQRLoginToken(Map<String, Object> data) {
        QRLoginToken token = new QRLoginToken();
        token.setTokenId((String) data.get("tokenId"));
        token.setUserId((String) data.get("userId"));
        token.setToken((String) data.get("token"));
        
        Long createdAtMillis = (Long) data.get("createdAt");
        Long expiresAtMillis = (Long) data.get("expiresAt");
        
        token.setCreatedAt(LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(createdAtMillis), 
            ZoneId.systemDefault()
        ));
        token.setExpiresAt(LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(expiresAtMillis), 
            ZoneId.systemDefault()
        ));
        
        token.setUsed((Boolean) data.get("used"));
        token.setPlatform((String) data.get("platform"));
        
        return token;
    }
}