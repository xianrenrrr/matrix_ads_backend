package com.example.demo.dao;

import com.example.demo.model.User;

public interface UserDao {
    User findByUsername(String username);
    User findByEmail(String email);
    User findByEmailAndRole(String email, String role);
    void save(User user);

    // Content Manager: manage created_Templates
    void addCreatedTemplate(String userId, String templateId);
    void removeCreatedTemplate(String userId, String templateId);
    java.util.Map<String, Boolean> getCreatedTemplates(String userId);
}
