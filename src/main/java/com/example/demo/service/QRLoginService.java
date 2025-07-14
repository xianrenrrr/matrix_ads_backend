package com.example.demo.service;

import com.example.demo.dao.QRLoginTokenDao;
import com.example.demo.dao.UserDao;
import com.example.demo.model.QRLoginToken;
import com.example.demo.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class QRLoginService {
    
    @Autowired
    private QRLoginTokenDao qrLoginTokenDao;
    
    @Autowired
    private UserDao userDao;
    
    private static final int QR_TOKEN_EXPIRY_MINUTES = 5;

    public Map<String, Object> generateQRLoginToken(String userId) {
        try {
            // Note: UserDao doesn't have findById, so we'll skip user validation here
            // The QR will be validated when used in verifyQRLogin
            
            // Delete any existing tokens for this user
            qrLoginTokenDao.deleteByUserId(userId);
            
            // Generate new token
            String tokenId = UUID.randomUUID().toString();
            String token = generateSecureToken();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiresAt = now.plusMinutes(QR_TOKEN_EXPIRY_MINUTES);
            
            QRLoginToken qrToken = new QRLoginToken(tokenId, userId, token, now, expiresAt);
            qrLoginTokenDao.save(qrToken);
            
            // Create QR code data
            Map<String, Object> qrData = new HashMap<>();
            qrData.put("token", token);
            qrData.put("userId", userId);
            qrData.put("platform", "miniprogram");
            qrData.put("expiresAt", expiresAt.toString());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("tokenId", tokenId);
            response.put("qrData", qrData);
            response.put("expiresAt", expiresAt);
            response.put("expiresInMinutes", QR_TOKEN_EXPIRY_MINUTES);
            
            return response;
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return errorResponse;
        }
    }
    
    public Map<String, Object> verifyQRLogin(String token, String userId, String platform) {
        try {
            // Find token
            Optional<QRLoginToken> qrTokenOpt = qrLoginTokenDao.findByToken(token);
            if (!qrTokenOpt.isPresent()) {
                throw new RuntimeException("Invalid QR code");
            }
            
            QRLoginToken qrToken = qrTokenOpt.get();
            
            // Verify token is valid
            if (!qrToken.isValid()) {
                throw new RuntimeException("QR code has expired or been used");
            }
            
            // Verify user ID matches
            if (!userId.equals(qrToken.getUserId())) {
                throw new RuntimeException("Invalid user ID");
            }
            
            // Get user details
            User user = userDao.findById(userId);
            if (user == null) {
                throw new RuntimeException("User not found");
            }
            if (!"content_creator".equals(user.getRole())) {
                throw new RuntimeException("Invalid user role");
            }
            
            // Mark token as used
            qrLoginTokenDao.markAsUsed(qrToken.getTokenId());
            
            // Generate new JWT token for mini program session
            String jwtToken = generateJWTToken(user);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("token", jwtToken);
            response.put("user", sanitizeUser(user));
            response.put("message", "Login successful");
            
            return response;
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return errorResponse;
        }
    }
    
    public void cleanupExpiredTokens() {
        try {
            qrLoginTokenDao.deleteExpiredTokens();
        } catch (Exception e) {
            System.err.println("Failed to cleanup expired QR tokens: " + e.getMessage());
        }
    }
    
    private String generateSecureToken() {
        // Generate a secure random token
        return UUID.randomUUID().toString() + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString();
    }
    
    private String generateJWTToken(User user) {
        // For now, return a simple token. In production, use proper JWT library
        return "jwt_" + user.getId() + "_" + System.currentTimeMillis() + "_miniprogram";
    }
    
    private Map<String, Object> sanitizeUser(User user) {
        Map<String, Object> sanitizedUser = new HashMap<>();
        sanitizedUser.put("id", user.getId());
        sanitizedUser.put("username", user.getUsername());
        sanitizedUser.put("email", user.getEmail());
        sanitizedUser.put("role", user.getRole());
        return sanitizedUser;
    }
}