package com.example.demo.controller;

import com.example.demo.dao.UserDao;
import com.example.demo.model.User;
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
            String password = loginRequest.get("password");
            
            if (usernameOrEmail == null || usernameOrEmail.trim().isEmpty()) {
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
            // Try to find by username first, then by email
            user = userDao.findByUsername(usernameOrEmail.trim());
            if (user == null) {
                user = userDao.findByEmail(usernameOrEmail.trim());
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
}
