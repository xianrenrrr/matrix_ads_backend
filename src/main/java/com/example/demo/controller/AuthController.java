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
    
    // TODO: GroupDao will be removed and functionality moved to InviteDao
    // @Autowired
    // private GroupDao groupDao;
    
    @Autowired
    private I18nService i18nService;

    // Token validation endpoint for mini app
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            if (token == null || token.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "HARDCODED_Token is required"); // TODO: Internationalize this message
                return ResponseEntity.badRequest().body(response);
            }

            // Simple token validation - in production this would validate JWT or session tokens
            // For now, just return success for any non-empty token
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "HARDCODED_Token is valid"); // TODO: Internationalize this message
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "HARDCODED_Token validation failed: " + e.getMessage()); // TODO: Internationalize this message
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

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
                response.put("error", "HARDCODED_Username already exists"); // TODO: Internationalize this message
                return ResponseEntity.badRequest().body(response);
            }
            if (userDao.findByEmailAndRole(user.getEmail(), user.getRole()) != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", i18nService.getMessage("registration.failed", language));
                response.put("error", "HARDCODED_Email already exists for this role"); // TODO: Internationalize this message
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
                response.put("message", "HARDCODED_Content creators should use the mini app for access"); // TODO: Internationalize this message
                return ResponseEntity.badRequest().body(response);
            }
            
            // Only allow Content Creators to login to mini program
            // Content managers should use web dashboard
            if (!"content_creator".equals(user.getRole()) && platform != null && "miniprogram".equals(platform)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "HARDCODED_Content managers should use the web dashboard for access"); // TODO: Internationalize this message
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
                response.put("message", "HARDCODED_Invite not found"); // TODO: Internationalize this message
                return ResponseEntity.notFound().build();
            }

            // For permanent groups (expiresAt is null), only check if status is active
            // For temporary invites (expiresAt is set), use full validation including expiration
            boolean isValidInvite = invite.getExpiresAt() == null ? 
                "active".equals(invite.getStatus()) : 
                invite.isValid();
                
            if (!isValidInvite) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                if (invite.getExpiresAt() == null) {
                    response.put("message", "HARDCODED_Group is not active"); // TODO: Internationalize this message
                } else {
                    response.put("message", invite.isExpired() ? "HARDCODED_Invite has expired" : "HARDCODED_Invite is not valid"); // TODO: Internationalize this message
                }
                return ResponseEntity.badRequest().body(response);
            }

            // Return invite data
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("groupName", invite.getGroupName());
            response.put("managerName", invite.getManagerName());
            response.put("role", invite.getRole());
            response.put("expiresAt", invite.getExpiresAt());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "HARDCODED_Failed to validate invite: " + e.getMessage()); // TODO: Internationalize this message
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
                    response.put("message", "HARDCODED_Phone number is required for content creators"); // TODO: Internationalize this message
                    return ResponseEntity.badRequest().body(response);
                }
                if (province == null || province.trim().isEmpty()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "HARDCODED_Province is required for content creators"); // TODO: Internationalize this message
                    return ResponseEntity.badRequest().body(response);
                }
                if (city == null || city.trim().isEmpty()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "HARDCODED_City is required for content creators"); // TODO: Internationalize this message
                    return ResponseEntity.badRequest().body(response);
                }
            }
            
            if (role != null && role.equals("content_manager") && (email == null || email.trim().isEmpty())) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "HARDCODED_Email is required for content managers"); // TODO: Internationalize this message
                return ResponseEntity.badRequest().body(response);
            }

            // Validate invite
            Invite invite = inviteDao.findByToken(inviteToken);
            
            // For permanent groups (expiresAt is null), only check if status is active
            // For temporary invites (expiresAt is set), use full validation including expiration
            boolean isValidInvite = invite != null && (invite.getExpiresAt() == null ? 
                "active".equals(invite.getStatus()) : 
                invite.isValid());
                
            if (!isValidInvite) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                if (invite == null) {
                    response.put("message", "HARDCODED_Invite not found"); // TODO: Internationalize this message
                } else if (invite.getExpiresAt() == null) {
                    response.put("message", "HARDCODED_Group is not active"); // TODO: Internationalize this message
                } else {
                    response.put("message", "HARDCODED_Invalid or expired invite"); // TODO: Internationalize this message
                }
                return ResponseEntity.badRequest().body(response);
            }

            // Group invites don't need email verification since they're open to multiple users

            // Check if username already exists
            if (userDao.findByUsername(username) != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "HARDCODED_Username already exists"); // TODO: Internationalize this message
                return ResponseEntity.badRequest().body(response);
            }

            // Check if phone already exists (for content creators)
            if (phone != null && !phone.trim().isEmpty()) {
                if (userDao.findByPhone(phone) != null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "HARDCODED_Phone number already exists"); // TODO: Internationalize this message
                    return ResponseEntity.badRequest().body(response);
                }
            }

            // Check if email already exists for content managers (only if email is provided)
            if (role != null && role.equals("content_manager") 
                && email != null && !email.trim().isEmpty()) {
                if (userDao.findByEmailAndRole(email, invite.getRole()) != null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "HARDCODED_Email already exists for this role"); // TODO: Internationalize this message
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

            // Handle group membership for group invites
            System.out.println("DEBUG: Processing invite for group creation");
            System.out.println("DEBUG: Invite ID: " + invite.getId());
            System.out.println("DEBUG: Invite GroupName: " + invite.getGroupName());
            System.out.println("DEBUG: Invite ManagerId: " + invite.getManagerId());
            
            if (invite.getGroupName() != null && !invite.getGroupName().trim().isEmpty()) {
                System.out.println("DEBUG: Group name found, proceeding with group creation/joining");
                
                // TODO: Group management will be handled directly in Invite model
                // For now, we'll track membership in the invite itself
                System.out.println("DEBUG: Group functionality temporarily disabled during refactor");
                
                // TODO: Add member tracking to Invite model
                // TODO: Remove Group model dependency
            } else {
                System.out.println("DEBUG: No group name found in invite, skipping group creation");
            }

            // Keep invite active for group invites (don't mark as accepted)
            // Individual users can join the same group invite multiple times

            // Return user data (without password) and group info
            user.setPassword(null);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", i18nService.getMessage("registration.success", language));
            response.put("user", user);
            
            // Include group information for group invites
            if (invite.getGroupName() != null && !invite.getGroupName().trim().isEmpty()) {
                response.put("groupName", invite.getGroupName());
                response.put("managerName", invite.getManagerName());
            }
            
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
