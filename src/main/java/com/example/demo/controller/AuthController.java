package com.example.demo.controller;

import com.example.demo.dao.UserDao;
import com.example.demo.dao.GroupDao;
import com.example.demo.model.User;
import com.example.demo.model.Group;
import com.example.demo.service.I18nService;
import com.example.demo.api.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private UserDao userDao;
    
    @Autowired
    private GroupDao groupDao;
    
    
    @Autowired
    private I18nService i18nService;

    // Token validation endpoint for mini app
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Void>> validateToken(@RequestBody Map<String, String> request,
                                                          @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        String token = request.get("token");
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token is required");
        }

        // Simple token validation - in production this would validate JWT or session tokens
        // For now, just return success for any non-empty token
        String message = i18nService.getMessage("token.valid", language);
        return ResponseEntity.ok(ApiResponse.ok(message));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<User>> signup(@RequestBody User user, 
                                                    @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        // Check if username or email already exists
        if (userDao.findByUsername(user.getUsername()) != null) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userDao.findByEmailAndRole(user.getEmail(), user.getRole()) != null) {
            throw new IllegalArgumentException("Email already exists for this role");
        }
            
            user.setId(UUID.randomUUID().toString());
            // Initialize fields based on role 
            if ("content_creator".equals(user.getRole())) {
                // Content creators get assigned through groups - no individual subscriptions
            } else if ("content_manager".equals(user.getRole())) {
                user.setCreated_Templates(new java.util.HashMap<>()); // Map<String, Boolean>
            }
            user.setNotifications(new java.util.HashMap<>()); // Map<String, Notification>
            
            // Save user with plain text password
            userDao.createUser(user);
            user.setPassword(null); // Don't return password
            
        String message = i18nService.getMessage("registration.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody java.util.Map<String, String> loginRequest,
                                                                  @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        String usernameOrEmail = loginRequest.get("username");
        String phone = loginRequest.get("phone");
        String password = loginRequest.get("password");
        String platform = loginRequest.get("platform");
        
        // For mini program (content creators), use phone login
        String loginField = (platform != null && "miniprogram".equals(platform)) ? phone : usernameOrEmail;
        
        if (loginField == null || loginField.trim().isEmpty()) {
            throw new IllegalArgumentException("Username, email, or phone is required");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
            
            User user = null;
            if (platform != null && "miniprogram".equals(platform)) {
                // Mini program login: find user by phone number
                user = userDao.findByPhone(loginField.trim());
            } else {
                // Web login: find by username first, then by email
                user = userDao.findByUsername(loginField.trim());
                if (user == null) {
                    user = userDao.findByEmail(loginField.trim());
                }
            }
            
        if (user == null) {
            throw new NoSuchElementException("User not found");
        }
        
        // Compare passwords directly (no encoding)
        if (!password.equals(user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        
        // Only allow Content Managers to login to web dashboard
        // Content creators can only login via mini program
        if (!"content_manager".equals(user.getRole()) && (platform == null || !"miniprogram".equals(platform))) {
            throw new IllegalArgumentException("Content creators should use the mini app for access");
        }
        
        // Only allow Content Creators to login to mini program
        // Content managers should use web dashboard
        if (!"content_creator".equals(user.getRole()) && platform != null && "miniprogram".equals(platform)) {
            throw new IllegalArgumentException("Content managers should use the web dashboard for access");
        }
            
            // Generate access token
            String token = "token_" + UUID.randomUUID().toString() + "_" + System.currentTimeMillis();
            
            // Create user response without password
            Map<String, Object> userResponse = new HashMap<>();
            userResponse.put("id", user.getId());
            userResponse.put("username", user.getUsername());
            userResponse.put("email", user.getEmail());
            userResponse.put("role", user.getRole());
            userResponse.put("groupId", user.getGroupId());  // Include groupId for mini program
            userResponse.put("phone", user.getPhone());
            
            // Return response matching mini program expectations
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("token", token);
            responseData.put("user", userResponse);
            
        String message = i18nService.getMessage("login.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }

    // Update profile (username and email)
    @PutMapping("/profile/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateProfile(@PathVariable String userId,
                                                                          @RequestBody Map<String, Object> request,
                                                                          @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);

        User existing = userDao.findById(userId);
        if (existing == null) {
            throw new NoSuchElementException("User not found");
        }

        String newUsername = (String) request.get("username");
        String newEmail = (String) request.get("email");

        if (newUsername != null && !newUsername.trim().isEmpty()) {
            existing.setUsername(newUsername.trim());
        }
        if (newEmail != null && !newEmail.trim().isEmpty()) {
            existing.setEmail(newEmail.trim());
        }

        userDao.save(existing);

        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put("id", existing.getId());
        userResponse.put("username", existing.getUsername());
        userResponse.put("email", existing.getEmail());
        userResponse.put("role", existing.getRole());

        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, userResponse));
    }

    // Change password
    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@RequestBody Map<String, String> request,
                                                            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        String userId = request.get("userId");
        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");

        if (userId == null || currentPassword == null || newPassword == null) {
            throw new IllegalArgumentException("Missing required fields");
        }

        User user = userDao.findById(userId);
        if (user == null) {
            throw new NoSuchElementException("User not found");
        }

        if (!currentPassword.equals(user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        user.setPassword(newPassword);
        userDao.save(user);
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message));
    }

    // Validate invite token
    @GetMapping("/validate-invite/{token}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateInvite(@PathVariable String token,
                                                                           @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        Group group = groupDao.findByToken(token);
        if (group == null) {
            throw new NoSuchElementException("Group not found with token: " + token);
        }

        // Check if group is active
        if (!"active".equals(group.getStatus())) {
            throw new IllegalArgumentException("Group is inactive");
        }

            // Return group data
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("groupName", group.getGroupName());
            responseData.put("managerName", group.getManagerName());
            responseData.put("role", "content_creator");
            
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }

    // Invite-based signup
    @PostMapping("/invite-signup")
    public ResponseEntity<ApiResponse<Map<String, Object>>> inviteSignup(@RequestBody Map<String, Object> requestBody,
                                                                         @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        String inviteToken = (String) requestBody.get("inviteToken");
        String username = (String) requestBody.get("username");
        String email = (String) requestBody.get("email");
        String phone = (String) requestBody.get("phone");
        String province = (String) requestBody.get("province");
        String city = (String) requestBody.get("city");
        String password = (String) requestBody.get("password");
        String role = (String) requestBody.get("role");

        // Validate required fields
        if (inviteToken == null || username == null || password == null) {
            throw new IllegalArgumentException("Invite token, username, and password are required");
        }
            
        // For content creators, phone, province, and city are required (no email needed)
        // For content managers, email is required
        if (role != null && role.equals("content_creator")) {
            if (phone == null || phone.trim().isEmpty()) {
                throw new IllegalArgumentException("Phone number is required for content creators");
            }
            if (province == null || province.trim().isEmpty()) {
                throw new IllegalArgumentException("Province is required for content creators");
            }
            if (city == null || city.trim().isEmpty()) {
                throw new IllegalArgumentException("City is required for content creators");
            }
        }
        
        if (role != null && role.equals("content_manager") && (email == null || email.trim().isEmpty())) {
            throw new IllegalArgumentException("Email is required for content managers");
        }

        // Validate group invite
        Group group = groupDao.findByToken(inviteToken);
        
        // Check if group exists and is active
        if (group == null) {
            throw new NoSuchElementException("Group not found with token: " + inviteToken);
        }
        
        
        // Check if group is active
        if (!"active".equals(group.getStatus())) {
            throw new IllegalArgumentException("Group is inactive");
        }

            // Group invites don't need email verification since they're open to multiple users

        // Check if username already exists
        if (userDao.findByUsername(username) != null) {
            throw new IllegalArgumentException("Username already exists");
        }

        // Check if phone already exists (for content creators)
        if (phone != null && !phone.trim().isEmpty()) {
            if (userDao.findByPhone(phone) != null) {
                throw new IllegalArgumentException("Phone number already exists");
            }
        }

        // Check if email already exists for content managers (only if email is provided)
        if (role != null && role.equals("content_manager") 
            && email != null && !email.trim().isEmpty()) {
            if (userDao.findByEmailAndRole(email, "content_creator") != null) {
                throw new IllegalArgumentException("Email already exists for this role");
            }
        }

            // Create new user
            User user = new User();
            user.setId(UUID.randomUUID().toString());
            user.setUsername(username);
            user.setPassword(password);
            user.setRole("content_creator"); // Content creators join through groups
            
            // Set role-specific fields
            if ("content_creator".equals(user.getRole())) {
                user.setPhone(phone);     // Content creators use phone
                user.setProvince(province); // Content creators have location
                user.setCity(city);
                user.setEmail(null);      // No email for content creators
            } else if ("content_manager".equals(user.getRole())) {
                user.setEmail(email);     // Content managers use email
                user.setPhone(null);      // No phone for content managers
                user.setProvince(null);   // No location for content managers
                user.setCity(null);
            }

            // Initialize fields based on role
            if ("content_creator".equals(user.getRole())) {
                // Content creators get assigned through groups
                String groupId = group.getId();
                user.setGroupId(groupId);
            } else if ("content_manager".equals(user.getRole())) {
                user.setCreated_Templates(new HashMap<>());
            }
            user.setNotifications(new HashMap<>());

            // Save user
            userDao.save(user);

            // Handle group membership
            if (group.getGroupName() != null && !group.getGroupName().trim().isEmpty()) {
                // Add the new user to the group's member list
                group.addMember(user.getId());
                
                // Update the group with the new member
                groupDao.update(group);
                
                System.out.println("User " + user.getId() + " added to group " + group.getGroupName());
            }

            // Keep invite active for group invites (don't mark as accepted)
            // Individual users can join the same group invite multiple times

            // Return user data (without password) and group info
            user.setPassword(null);
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("user", user);
            responseData.put("role", user.getRole());
            
            // Include group information
            if (group.getGroupName() != null && !group.getGroupName().trim().isEmpty()) {
                responseData.put("groupName", group.getGroupName());
                responseData.put("managerName", group.getManagerName());
            }
            
        String message = i18nService.getMessage("registration.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }
}
