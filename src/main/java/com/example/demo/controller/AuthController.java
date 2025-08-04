package com.example.demo.controller;

import com.example.demo.dao.UserDao;
import com.example.demo.dao.InviteDao;
import com.example.demo.model.User;
import com.example.demo.model.Invite;
import com.example.demo.service.I18nService;
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

    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@RequestBody User user, 
                                                      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        try {
            String language = i18nService.detectLanguageFromHeader(acceptLanguage);
            
            // Check if username or email already exists
            if (userDao.findByUsername(user.getUsername()) != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", i18nService.getMessage("registration.failed", language));
                response.put("error", "Username already exists");
                return ResponseEntity.badRequest().body(response);
            }
            if (userDao.findByEmailAndRole(user.getEmail(), user.getRole()) != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", i18nService.getMessage("registration.failed", language));
                response.put("error", "Email already exists for this role");
                return ResponseEntity.badRequest().body(response);
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
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", i18nService.getMessage("registration.success", language));
            response.put("user", user);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            String language = i18nService.detectLanguageFromHeader(acceptLanguage);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", i18nService.getMessage("registration.failed", language));
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody java.util.Map<String, String> loginRequest,
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
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", i18nService.getMessage("bad.request", language));
                return ResponseEntity.badRequest().body(response);
            }
            if (password == null || password.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", i18nService.getMessage("bad.request", language));
                return ResponseEntity.badRequest().body(response);
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
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", i18nService.getMessage("user.not.found", language));
                return ResponseEntity.badRequest().body(response);
            }
            
            if (!password.equals(user.getPassword())) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", i18nService.getMessage("login.invalid.credentials", language));
                return ResponseEntity.badRequest().body(response);
            }
            
            // Only allow Content Managers to login to web dashboard
            // Content creators can only login via mini program
            if (!"content_manager".equals(user.getRole()) && (platform == null || !"miniprogram".equals(platform))) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Content creators should use the mini app for access");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Only allow Content Creators to login to mini program
            // Content managers should use web dashboard
            if (!"content_creator".equals(user.getRole()) && platform != null && "miniprogram".equals(platform)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Content managers should use the web dashboard for access");
                return ResponseEntity.badRequest().body(response);
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
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("token", token);
            response.put("user", userResponse);
            response.put("message", i18nService.getMessage("login.success", language));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            String language = i18nService.detectLanguageFromHeader(acceptLanguage);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", i18nService.getMessage("login.failed", language));
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // Validate invite token
    @GetMapping("/validate-invite/{token}")
    public ResponseEntity<Map<String, Object>> validateInvite(@PathVariable String token,
                                                              @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        try {
            String language = i18nService.detectLanguageFromHeader(acceptLanguage);
            
            Invite invite = inviteDao.findByToken(token);
            if (invite == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Invite not found");
                return ResponseEntity.notFound().build();
            }

            if (!invite.isValid()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", invite.isExpired() ? "Invite has expired" : "Invite is not valid");
                return ResponseEntity.badRequest().body(response);
            }

            // Return invite data
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("inviteeName", invite.getInviteeName());
            response.put("inviteeEmail", invite.getInviteeEmail());
            response.put("managerName", invite.getManagerName());
            response.put("role", invite.getRole());
            response.put("expiresAt", invite.getExpiresAt());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to validate invite: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Invite-based signup
    @PostMapping("/invite-signup")
    public ResponseEntity<Map<String, Object>> inviteSignup(@RequestBody Map<String, Object> requestBody,
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
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", i18nService.getMessage("bad.request", language));
                return ResponseEntity.badRequest().body(response);
            }
            
            // For content creators, phone, province, and city are required (no email needed)
            // For content managers, email is required
            if (role != null && role.equals("content_creator")) {
                if (phone == null || phone.trim().isEmpty()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Phone number is required for content creators");
                    return ResponseEntity.badRequest().body(response);
                }
                if (province == null || province.trim().isEmpty()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Province is required for content creators");
                    return ResponseEntity.badRequest().body(response);
                }
                if (city == null || city.trim().isEmpty()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "City is required for content creators");
                    return ResponseEntity.badRequest().body(response);
                }
            }
            
            if (role != null && role.equals("content_manager") && (email == null || email.trim().isEmpty())) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Email is required for content managers");
                return ResponseEntity.badRequest().body(response);
            }

            // Validate invite
            Invite invite = inviteDao.findByToken(inviteToken);
            if (invite == null || !invite.isValid()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Invalid or expired invite");
                return ResponseEntity.badRequest().body(response);
            }

            // For content managers only: verify email matches invite if both are provided
            if (role != null && role.equals("content_manager") 
                && invite.getInviteeEmail() != null && !invite.getInviteeEmail().trim().isEmpty() 
                && email != null && !email.trim().isEmpty()) {
                if (!email.equals(invite.getInviteeEmail())) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Email does not match invite");
                    return ResponseEntity.badRequest().body(response);
                }
            }

            // Check if username already exists
            if (userDao.findByUsername(username) != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Username already exists");
                return ResponseEntity.badRequest().body(response);
            }

            // Check if phone already exists (for content creators)
            if (phone != null && !phone.trim().isEmpty()) {
                if (userDao.findByPhone(phone) != null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Phone number already exists");
                    return ResponseEntity.badRequest().body(response);
                }
            }

            // Check if email already exists for content managers (only if email is provided)
            if (role != null && role.equals("content_manager") 
                && email != null && !email.trim().isEmpty()) {
                if (userDao.findByEmailAndRole(email, invite.getRole()) != null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Email already exists for this role");
                    return ResponseEntity.badRequest().body(response);
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

            // Mark invite as accepted
            invite.setStatus("accepted");
            invite.setAcceptedAt(new java.util.Date());
            inviteDao.save(invite);

            // Return user data (without password)
            user.setPassword(null);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", i18nService.getMessage("registration.success", language));
            response.put("user", user);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            String language = i18nService.detectLanguageFromHeader(acceptLanguage);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", i18nService.getMessage("registration.failed", language));
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
