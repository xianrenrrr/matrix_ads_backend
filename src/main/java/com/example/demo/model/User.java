package com.example.demo.model;

public class User {
    private String id;
    private String username;
    private String email;
    private String password;
    private String role; // "content_creator" or "content_manager"

    public User() {}

    public User(String id, String username, String email, String password, String role) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
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
}

