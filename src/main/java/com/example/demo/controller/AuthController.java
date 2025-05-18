package com.example.demo.controller;

import com.example.demo.dao.UserDao;
import com.example.demo.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private UserDao userDao;

    @PostMapping("/signup")
    public User signup(@RequestBody User user) {
        // Check if username or email already exists
        if (userDao.findByUsername(user.getUsername()) != null) {
            throw new RuntimeException("Username already exists");
        }
        if (userDao.findByEmail(user.getEmail()) != null) {
            throw new RuntimeException("Email already exists");
        }
        user.setId(UUID.randomUUID().toString());
        // Initialize new fields for db.md compatibility
        user.setSubscribedTemplates(new java.util.HashMap<>()); // Map<String, Boolean>
        user.setNotifications(new java.util.HashMap<>()); // Map<String, Notification>
        userDao.save(user);
        user.setPassword(null); // Don't return password
        return user;
    }

    @PostMapping("/login")
    public User login(@RequestBody User loginRequest) {
        User user = null;
        // Allow login by either username or email
        if (loginRequest.getUsername() != null && !loginRequest.getUsername().isEmpty()) {
            user = userDao.findByUsername(loginRequest.getUsername());
        }
        if (user == null && loginRequest.getEmail() != null && !loginRequest.getEmail().isEmpty()) {
            user = userDao.findByEmail(loginRequest.getEmail());
        }
        if (user == null || !user.getPassword().equals(loginRequest.getPassword())) {
            throw new RuntimeException("Invalid username/email or password");
        }
        user.setPassword(null); // Don't return password
        return user;
    }
}
