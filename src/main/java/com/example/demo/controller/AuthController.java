package com.example.demo.controller;

import com.example.demo.dao.UserDao;
import com.example.demo.dao.InviteDao;
import com.example.demo.model.User;
import com.example.demo.model.Invite;
import com.example.demo.service.I18nService;
import com.example.demo.api.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private UserDao userDao;
    
    @Autowired
    private InviteDao inviteDao;
    
    
    @Autowired
    private I18nService i18nService;

    // Token validation endpoint for mini app
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Void>> validateToken(@RequestBody Map<String, String> request,
                                                          @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        try {
            String language = i18nService.detectLanguageFromHeader(acceptLanguage);
            String token = request.get("token");
            if (token == null || token.isEmpty()) {
                String message = i18nService.getMessage("token.required", language);
                return ResponseEntity.badRequest().body(ApiResponse.fail(message));
            }

            // Simple token validation - in production this would validate JWT or session tokens
            // For now, just return success for any non-empty token
            String message = i18nService.getMessage("token.valid", language);
            return ResponseEntity.ok(ApiResponse.ok(message));

        } catch (Exception e) {
            String language = i18nService.detectLanguageFromHeader(acceptLanguage);
            String message = i18nService.getMessage("token.validation.failed", language);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(message, e.getMessage()));
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<User>> signup(@RequestBody User user, 
                                                    @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        try {
            String language = i18nService.detectLanguageFromHeader(acceptLanguage);
            
            // Check if username or email already exists
            if (userDao.findByUsername(user.getUsername()) != null) {
                String message = i18nService.getMessage("username.exists", language);
                return ResponseEntity.badRequest().body(ApiResponse.fail(message));
            }
            if (userDao.findByEmailAndRole(user.getEmail(), user.getRole()) != null) {
                String message = i18nService.getMessage("email.exists", language);
                return ResponseEntity.badRequest().body(ApiResponse.fail(message));
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
            
            String message = i18nService.getMessage("registration.success", language);
            return ResponseEntity.ok(ApiResponse.ok(message, user));
            
        } catch (Exception e) {
            String language = i18nService.detectLanguageFromHeader(acceptLanguage);
            String message = i18nService.getMessage("registration.failed", language);
            return ResponseEntity.badRequest().body(ApiResponse.fail(message, e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody java.util.Map<String, String> loginRequest,
                                                                  @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        try {
            String language = i18nService.detectLanguageFromHeader(acceptLanguage);
            String usernameOrEmail = loginRequest.get("username");
            String phone = loginRequest.get("phone");
            String password = loginRequest.get("password");
            String platform = loginRequest.get("platform");
            
            // For mini program (content creators), use phone login
            String loginField = (platform != null && "miniprogram".equals(platform)) ? phone : usernameOrEmail;
            
            if (loginField == null || loginField.trim().isEmpty()) {
                String message = i18nService.getMessage("bad.request", language);
                return ResponseEntity.badRequest().body(ApiResponse.fail(message));
            }
            if (password == null || password.trim().isEmpty()) {
                String message = i18nService.getMessage("bad.request", language);
                return ResponseEntity.badRequest().body(ApiResponse.fail(message));
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
                String message = i18nService.getMessage("user.not.found", language);
                return ResponseEntity.badRequest().body(ApiResponse.fail(message));
            }
            
            if (!password.equals(user.getPassword())) {
                String message = i18nService.getMessage("login.invalid.credentials", language);
                return ResponseEntity.badRequest().body(ApiResponse.fail(message));
            }
            
            // Only allow Content Managers to login to web dashboard
            // Content creators can only login via mini program
            if (!"content_manager".equals(user.getRole()) && (platform == null || !"miniprogram".equals(platform))) {
                String message = i18nService.getMessage("forbidden", language);
                return ResponseEntity.badRequest().body(ApiResponse.fail(message, "Content creators should use the mini app for access"));
            }
            
            // Only allow Content Creators to login to mini program
            // Content managers should use web dashboard
            if (!"content_creator".equals(user.getRole()) && platform != null && "miniprogram".equals(platform)) {
                String message = i18nService.getMessage("forbidden", language);
                return ResponseEntity.badRequest().body(ApiResponse.fail(message, "Content managers should use the web dashboard for access"));
            }
            
            // Generate access token
            String token = "token_" + UUID.randomUUID().toString() + "_" + System.currentTimeMillis();
            
            // Create user response without password
            Map<String, Object> userResponse = new HashMap<>();
            userResponse.put("id", user.getId());
            userResponse.put("username", user.getUsername());
            userResponse.put("email", user.getEmail());
            userResponse.put("role", user.getRole());
            
            // Return response matching mini program expectations
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("token", token);
            responseData.put("user", userResponse);
            
            String message = i18nService.getMessage("login.success", language);
            return ResponseEntity.ok(ApiResponse.ok(message, responseData));
            
        } catch (Exception e) {
            String language = i18nService.detectLanguageFromHeader(acceptLanguage);
            String message = i18nService.getMessage("login.failed", language);
            return ResponseEntity.badRequest().body(ApiResponse.fail(message, e.getMessage()));
        }
    }

    // Validate invite token
    @GetMapping("/validate-invite/{token}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateInvite(@PathVariable String token,
                                                                           @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        try {
            String language = i18nService.detectLanguageFromHeader(acceptLanguage);
            
            Invite invite = inviteDao.findByToken(token);
            if (invite == null) {
                String message = i18nService.getMessage("invite.not_found", language);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(message));
            }

            // Use InviteValidity utility for centralized validation logic
            if (!com.example.demo.util.InviteValidity.isActive(invite)) {
                String message = i18nService.getMessage("group.inactive", language);
                return ResponseEntity.badRequest().body(ApiResponse.fail(message));
            }

            // Return invite data
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("groupName", invite.getGroupName());
            responseData.put("managerName", invite.getManagerName());
            responseData.put("role", invite.getRole());
            
            String message = i18nService.getMessage("operation.success", language);
            return ResponseEntity.ok(ApiResponse.ok(message, responseData));

        } catch (Exception e) {
            String language = i18nService.detectLanguageFromHeader(acceptLanguage);
            String message = i18nService.getMessage("server.error", language);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(message, "Failed to validate invite: " + e.getMessage()));
        }
    }

    // Invite-based signup
    @PostMapping("/invite-signup")
    public ResponseEntity<ApiResponse<Map<String, Object>>> inviteSignup(@RequestBody Map<String, Object> requestBody,
                                                                         @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        try {
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
                String message = i18nService.getMessage("bad.request", language);
                return ResponseEntity.badRequest().body(ApiResponse.fail(message));
            }
            
            // For content creators, phone, province, and city are required (no email needed)
            // For content managers, email is required
            if (role != null && role.equals("content_creator")) {
                if (phone == null || phone.trim().isEmpty()) {
                    String message = i18nService.getMessage("bad.request", language);
                    return ResponseEntity.badRequest().body(ApiResponse.fail(message, "Phone number is required for content creators"));
                }
                if (province == null || province.trim().isEmpty()) {
                    String message = i18nService.getMessage("bad.request", language);
                    return ResponseEntity.badRequest().body(ApiResponse.fail(message, "Province is required for content creators"));
                }
                if (city == null || city.trim().isEmpty()) {
                    String message = i18nService.getMessage("bad.request", language);
                    return ResponseEntity.badRequest().body(ApiResponse.fail(message, "City is required for content creators"));
                }
            }
            
            if (role != null && role.equals("content_manager") && (email == null || email.trim().isEmpty())) {
                String message = i18nService.getMessage("bad.request", language);
                return ResponseEntity.badRequest().body(ApiResponse.fail(message, "Email is required for content managers"));
            }

            // Validate invite
            Invite invite = inviteDao.findByToken(inviteToken);
            
            // Since invites are permanent now, just check if it exists and is active
            if (invite == null) {
                String message = i18nService.getMessage("invite.not_found", language);
                return ResponseEntity.badRequest().body(ApiResponse.fail(message));
            }
            
            // Use InviteValidity utility for centralized validation logic
            if (!com.example.demo.util.InviteValidity.isActive(invite)) {
                String message = i18nService.getMessage("group.inactive", language);
                return ResponseEntity.badRequest().body(ApiResponse.fail(message));
            }

            // Group invites don't need email verification since they're open to multiple users

            // Check if username already exists
            if (userDao.findByUsername(username) != null) {
                String message = i18nService.getMessage("username.exists", language);
                return ResponseEntity.badRequest().body(ApiResponse.fail(message));
            }

            // Check if phone already exists (for content creators)
            if (phone != null && !phone.trim().isEmpty()) {
                if (userDao.findByPhone(phone) != null) {
                    String message = i18nService.getMessage("bad.request", language);
                    return ResponseEntity.badRequest().body(ApiResponse.fail(message, "Phone number already exists"));
                }
            }

            // Check if email already exists for content managers (only if email is provided)
            if (role != null && role.equals("content_manager") 
                && email != null && !email.trim().isEmpty()) {
                if (userDao.findByEmailAndRole(email, invite.getRole()) != null) {
                    String message = i18nService.getMessage("email.exists", language);
                    return ResponseEntity.badRequest().body(ApiResponse.fail(message));
                }
            }

            // Create new user
            User user = new User();
            user.setId(UUID.randomUUID().toString());
            user.setUsername(username);
            user.setPassword(password);
            user.setRole(invite.getRole()); // Use role from invite
            
            // Set role-specific fields
            if ("content_creator".equals(invite.getRole())) {
                user.setPhone(phone);     // Content creators use phone
                user.setProvince(province); // Content creators have location
                user.setCity(city);
                user.setEmail(null);      // No email for content creators
            } else if ("content_manager".equals(invite.getRole())) {
                user.setEmail(email);     // Content managers use email
                user.setPhone(null);      // No phone for content managers
                user.setProvince(null);   // No location for content managers
                user.setCity(null);
            }

            // Initialize fields based on role
            if ("content_creator".equals(user.getRole())) {
                user.setSubscribed_Templates(new HashMap<>());
            } else if ("content_manager".equals(user.getRole())) {
                user.setCreated_Templates(new HashMap<>());
            }
            user.setNotifications(new HashMap<>());

            // Save user
            userDao.save(user);

            // Handle group membership for group invites
            
            if (invite.getGroupName() != null && !invite.getGroupName().trim().isEmpty()) {
                
                // TODO: Group management will be handled directly in Invite model
                // For now, we'll track membership in the invite itself
                
                // TODO: Add member tracking to Invite model
                // TODO: Remove Group model dependency
            } else {
            }

            // Keep invite active for group invites (don't mark as accepted)
            // Individual users can join the same group invite multiple times

            // Return user data (without password) and group info
            user.setPassword(null);
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("user", user);
            
            // Include group information for group invites
            if (invite.getGroupName() != null && !invite.getGroupName().trim().isEmpty()) {
                responseData.put("groupName", invite.getGroupName());
                responseData.put("managerName", invite.getManagerName());
            }
            
            String message = i18nService.getMessage("registration.success", language);
            return ResponseEntity.ok(ApiResponse.ok(message, responseData));

        } catch (Exception e) {
            String language = i18nService.detectLanguageFromHeader(acceptLanguage);
            String message = i18nService.getMessage("registration.failed", language);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(message, e.getMessage()));
        }
    }
}
