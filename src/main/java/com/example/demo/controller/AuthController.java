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
        // Check if user already exists
        if (userDao.findByUsername(user.getUsername()) != null) {
            throw new RuntimeException("Username already exists");
        }
        user.setId(UUID.randomUUID().toString());
        userDao.save(user);
        user.setPassword(null); // Don't return password
        return user;
    }

    @PostMapping("/login")
    public User login(@RequestBody User loginRequest) {
        User user = userDao.findByUsername(loginRequest.getUsername());
        if (user == null || !user.getPassword().equals(loginRequest.getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }
        user.setPassword(null); // Don't return password
        return user;
    }
}
