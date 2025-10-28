package com.example.demo.dao;

import com.example.demo.model.User;

public interface UserDao {
    User findByUsername(String username);
    User findByEmail(String email);
    User findByEmailAndRole(String email, String role);
    User findByPhone(String phone);
    User findById(String id);
    void save(User user);

    // Content Manager: manage created_Templates
    void addCreatedTemplate(String userId, String templateId);
    void removeCreatedTemplate(String userId, String templateId);
    java.util.Map<String, Boolean> getCreatedTemplates(String userId);
    
    // Content Creator: manage subscribed_Templates
    void addSubscribedTemplate(String userId, String templateId);
    void removeSubscribedTemplate(String userId, String templateId);
    java.util.Map<String, Boolean> getSubscribedTemplates(String userId);
    
    // Find users by role
    java.util.List<User> findByRole(String role);
    
    // IAM methods
    String createUser(User user);
    User authenticateUser(String username, String password);
    java.util.List<User> findByCreatedBy(String managerId);
    void delete(String userId);
}
