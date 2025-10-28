package com.example.demo.migration;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.api.core.ApiFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.util.HashMap;
import java.util.Map;

/**
 * Migration script to set existing users as CONTENT_MANAGER
 * Run once to upgrade existing users to the new IAM system
 * 
 * Usage: Run this as a Spring Boot application
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.example.demo")
public class SetExistingUsersAsManagers implements CommandLineRunner {

    @Autowired
    private Firestore firestore;

    public static void main(String[] args) {
        SpringApplication.run(SetExistingUsersAsManagers.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== Starting User Role Migration ===");
        System.out.println("Setting all existing users to CONTENT_MANAGER role...");
        
        try {
            // Get all users
            ApiFuture<QuerySnapshot> future = firestore.collection("users").get();
            QuerySnapshot querySnapshot = future.get();
            
            int totalUsers = querySnapshot.size();
            int updatedUsers = 0;
            int skippedUsers = 0;
            
            System.out.println("Found " + totalUsers + " users to process");
            
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                String userId = document.getId();
                Map<String, Object> userData = document.getData();
                
                // Check if user already has a role
                String existingRole = (String) userData.get("role");
                
                if (existingRole != null && !existingRole.isEmpty()) {
                    System.out.println("User " + userId + " already has role: " + existingRole + " - skipping");
                    skippedUsers++;
                    continue;
                }
                
                // Update user with CONTENT_MANAGER role
                Map<String, Object> updates = new HashMap<>();
                updates.put("role", "CONTENT_MANAGER");
                updates.put("createdBy", null); // Content managers are not created by anyone
                
                firestore.collection("users").document(userId).update(updates).get();
                
                String username = (String) userData.get("username");
                System.out.println("✓ Updated user: " + username + " (ID: " + userId + ") -> CONTENT_MANAGER");
                updatedUsers++;
            }
            
            System.out.println("\n=== Migration Complete ===");
            System.out.println("Total users: " + totalUsers);
            System.out.println("Updated: " + updatedUsers);
            System.out.println("Skipped: " + skippedUsers);
            System.out.println("All existing users are now CONTENT_MANAGER!");
            
        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
        
        System.exit(0);
    }
}
