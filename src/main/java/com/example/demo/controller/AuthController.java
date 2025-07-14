package com.example.demo.controller;

import com.example.demo.dao.UserDao;
import com.example.demo.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    @Autowired
    private UserDao userDao;

    @PostMapping("/signup")
    public User signup(@RequestBody User user) {
        // Check if username or email already exists
        if (userDao.findByUsername(user.getUsername()) != null) {
            throw new RuntimeException("Username already exists");
        }
        if (userDao.findByEmailAndRole(user.getEmail(), user.getRole()) != null) {
            throw new RuntimeException("Email already exists for this role");
        }
        user.setId(UUID.randomUUID().toString());
        // Initialize fields based on role for db.md compatibility
        if ("content_creator".equals(user.getRole())) {
            user.setSubscribed_Templates(new java.util.HashMap<>()); // Map<String, Boolean>
        } else if ("content_manager".equals(user.getRole())) {
            user.setCreated_Templates(new java.util.HashMap<>()); // Map<String, Boolean>
        }
        user.setNotifications(new java.util.HashMap<>()); // Map<String, Notification>
        userDao.save(user);
        user.setPassword(null); // Don't return password
        return user;
    }

    @PostMapping("/login")
    public java.util.Map<String, Object> login(@RequestBody java.util.Map<String, String> loginRequest) {
        try {
            String usernameOrEmail = loginRequest.get("username");
            String password = loginRequest.get("password");
            
            if (usernameOrEmail == null || usernameOrEmail.trim().isEmpty()) {
                throw new RuntimeException("Username or email is required");
            }
            if (password == null || password.trim().isEmpty()) {
                throw new RuntimeException("Password is required");
            }
            
            User user = null;
            // Try to find by username first, then by email
            user = userDao.findByUsername(usernameOrEmail.trim());
            if (user == null) {
                user = userDao.findByEmail(usernameOrEmail.trim());
            }
            
            if (user == null) {
                throw new RuntimeException("User not found");
            }
            
            if (!password.equals(user.getPassword())) {
                throw new RuntimeException("Invalid password");
            }
            
            // Generate access token
            String token = "token_" + UUID.randomUUID().toString() + "_" + System.currentTimeMillis();
            
            // Create user response without password
            java.util.Map<String, Object> userResponse = new java.util.HashMap<>();
            userResponse.put("id", user.getId());
            userResponse.put("username", user.getUsername());
            userResponse.put("email", user.getEmail());
            userResponse.put("role", user.getRole());
            
            // Return response matching mini program expectations
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("success", true);
            response.put("token", token);
            response.put("user", userResponse);
            response.put("message", "Login successful");
            
            return response;
            
        } catch (Exception e) {
            java.util.Map<String, Object> errorResponse = new java.util.HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return errorResponse;
        }
    }
}
