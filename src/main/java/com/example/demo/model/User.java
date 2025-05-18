package com.example.demo.model;

public class User {
    private String id;
    private String username;
    private String email;
    private String password;
    private String role; // "content_creator" or "content_manager"

    // New fields for db.md compatibility
    private java.util.Map<String, Boolean> subscribed_Templates;
    private java.util.Map<String, Boolean> created_Templates;
    private java.util.Map<String, Notification> notifications;

    public User() {}

    public User(String id, String username, String email, String password, String role) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
        this.subscribed_Templates = new java.util.HashMap<>();
        this.created_Templates = new java.util.HashMap<>();
        this.notifications = new java.util.HashMap<>();
    }

    // Optional: backward compatibility constructor
    public User(String id, String username, String password, String role) {
        this(id, username, null, password, role);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public java.util.Map<String, Boolean> getSubscribed_Templates() { return subscribed_Templates; }
    public void setSubscribed_Templates(java.util.Map<String, Boolean> subscribed_Templates) { this.subscribed_Templates = subscribed_Templates; }

    public java.util.Map<String, Boolean> getCreated_Templates() { return created_Templates; }
    public void setCreated_Templates(java.util.Map<String, Boolean> created_Templates) { this.created_Templates = created_Templates; }

    public java.util.Map<String, Notification> getNotifications() { return notifications; }
    public void setNotifications(java.util.Map<String, Notification> notifications) { this.notifications = notifications; }
}

