package com.example.demo.controller;

import com.example.demo.service.QRLoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = {"http://localhost:4040", "http://localhost:3000"})
public class QRLoginController {
    
    @Autowired
    private QRLoginService qrLoginService;
    
    @PostMapping("/generate-qr")
    public ResponseEntity<Map<String, Object>> generateQRCode(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        
        if (userId == null || userId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "User ID is required"
            ));
        }
        
        Map<String, Object> result = qrLoginService.generateQRLoginToken(userId);
        
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @PostMapping("/qr-login")
    public ResponseEntity<Map<String, Object>> verifyQRLogin(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        String userId = request.get("userId");
        String platform = request.get("platform");
        
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Token is required"
            ));
        }
        
        if (userId == null || userId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "User ID is required"
            ));
        }
        
        Map<String, Object> result = qrLoginService.verifyQRLogin(token, userId, platform);
        
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @PostMapping("/cleanup-qr-tokens")
    public ResponseEntity<Map<String, Object>> cleanupExpiredTokens() {
        try {
            qrLoginService.cleanupExpiredTokens();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Expired tokens cleaned up successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to cleanup tokens: " + e.getMessage()
            ));
        }
    }
}