package com.example.demo.model;

public class User {
    private String id;
    private String username;    
    private String email;
    // Notification preferences
    private String notificationEmail;
    private Boolean emailNotificationsEnabled;
    private Boolean inAppNotificationsEnabled;
    private String phone;
    private String province;
    private String city;
    private String password;
    private String role; // "content_creator" or "content_manager"
    private String groupId; // Group ID for content creators

    // New fields for db.md compatibility
    private java.util.Map<String, Boolean> created_Templates;
    private java.util.Map<String, Notification> notifications;

    public User() {}

    public User(String id, String username,String email, String password, String role) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
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

    public String getNotificationEmail() { return notificationEmail; }
    public void setNotificationEmail(String notificationEmail) { this.notificationEmail = notificationEmail; }

    public Boolean getEmailNotificationsEnabled() { return emailNotificationsEnabled; }
    public void setEmailNotificationsEnabled(Boolean emailNotificationsEnabled) { this.emailNotificationsEnabled = emailNotificationsEnabled; }

    public Boolean getInAppNotificationsEnabled() { return inAppNotificationsEnabled; }
    public void setInAppNotificationsEnabled(Boolean inAppNotificationsEnabled) { this.inAppNotificationsEnabled = inAppNotificationsEnabled; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public java.util.Map<String, Boolean> getCreated_Templates() { return created_Templates; }
    public void setCreated_Templates(java.util.Map<String, Boolean> created_Templates) { this.created_Templates = created_Templates; }

    public java.util.Map<String, Notification> getNotifications() { return notifications; }
    public void setNotifications(java.util.Map<String, Notification> notifications) { this.notifications = notifications; }
}
