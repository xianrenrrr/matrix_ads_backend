package com.example.demo.dao;

import com.example.demo.model.QRLoginToken;
import java.util.Optional;

public interface QRLoginTokenDao {
    void save(QRLoginToken token);
    Optional<QRLoginToken> findByTokenId(String tokenId);
    Optional<QRLoginToken> findByToken(String token);
    void markAsUsed(String tokenId);
    void deleteExpiredTokens();
    void deleteByUserId(String userId);
}