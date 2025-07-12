package com.example.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ScheduledTasks {
    
    @Autowired
    private QRLoginService qrLoginService;
    
    // Clean up expired QR tokens every 10 minutes
    @Scheduled(fixedRate = 600000) // 10 minutes in milliseconds
    public void cleanupExpiredQRTokens() {
        try {
            qrLoginService.cleanupExpiredTokens();
            System.out.println("Cleaned up expired QR tokens at: " + new java.util.Date());
        } catch (Exception e) {
            System.err.println("Failed to cleanup expired QR tokens: " + e.getMessage());
        }
    }
}