package com.example.demo.controller.contentmanager;

import com.example.demo.constants.UserRole;
import com.example.demo.dao.UserDao;
import com.example.demo.model.User;
import com.example.demo.service.PermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing employee accounts
 * Only accessible by content managers
 */
@RestController
@RequestMapping("/content-manager/users")
public class UserManagementController {
    
    private static final Logger log = LoggerFactory.getLogger(UserManagementController.class);
    
    @Autowired
    private PermissionService permissionService;
    
    @Autowired
    private UserDao userDao;
    
    /**
     * Create employee account (only for content managers)
     */
    @PostMapping("/employees")
    public ResponseEntity<?> createEmployee(
        @RequestParam String managerId,
        @RequestBody CreateEmployeeRequest request
    ) {
        log.info("Creating employee account: manager={}, username={}", managerId, request.getUsername());
        log.info("Password received (length): {}", request.getPassword() != null ? request.getPassword().length() : 0);
        
        // Check permission
        if (!permissionService.canCreateEmployees(managerId)) {
            log.warn("User {} does not have permission to create employees", managerId);
            return ResponseEntity.status(403).body(Map.of(
                "error", "Permission denied",
                "message", "只有管理者可以创建员工账号"
            ));
        }
        
        // Get manager info
        User manager = userDao.findById(managerId);
        if (manager == null) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "Manager not found",
                "message", "管理者不存在"
            ));
        }
        
        // Check if username already exists
        User existingUser = userDao.findByUsername(request.getUsername());
        if (existingUser != null) {
            return ResponseEntity.status(400).body(Map.of(
                "error", "Username exists",
                "message", "用户名已存在"
            ));
        }
        
        // Create employee user
        User employee = new User();
        employee.setUsername(request.getUsername());
        employee.setPassword(request.getPassword()); // Will be encoded by createUser
        employee.setRole(UserRole.EMPLOYEE);
        employee.setCreatedBy(managerId);
        employee.setOrganizationId(manager.getOrganizationId() != null ? 
            manager.getOrganizationId() : managerId);
        employee.setCreatedAt(new Date());
        
        if (request.getEmail() != null) {
            employee.setEmail(request.getEmail());
        }
        
        log.info("Creating user with password (before encoding): {}", request.getPassword());
        String employeeId = userDao.createUser(employee);
        employee.setId(employeeId);
        
        // Verify the user was created correctly
        User verifyUser = userDao.findById(employeeId);
        if (verifyUser != null) {
            log.info("Employee created - stored password starts with: {}", 
                verifyUser.getPassword() != null ? verifyUser.getPassword().substring(0, Math.min(10, verifyUser.getPassword().length())) : "null");
        }
        
        log.info("Employee account created: id={}, username={}, createdBy={}", 
            employeeId, employee.getUsername(), managerId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("employeeId", employeeId);
        response.put("username", employee.getUsername());
        response.put("role", employee.getRole());
        response.put("createdAt", employee.getCreatedAt());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * List employees created by this manager
     */
    @GetMapping("/employees")
    public ResponseEntity<?> listEmployees(@RequestParam String managerId) {
        log.info("Listing employees for manager: {}", managerId);
        
        // Get manager info for debugging
        User manager = userDao.findById(managerId);
        if (manager == null) {
            log.error("Manager not found: {}", managerId);
            return ResponseEntity.status(404).body(Map.of(
                "error", "Manager not found",
                "message", "管理者不存在"
            ));
        }
        
        log.info("Manager found: id={}, username={}, role={}", 
            managerId, manager.getUsername(), manager.getRole());
        
        // Check permission
        if (!permissionService.canCreateEmployees(managerId)) {
            log.warn("Manager {} does not have permission to list employees (role: {})", 
                managerId, manager.getRole());
            return ResponseEntity.status(403).body(Map.of(
                "error", "Permission denied",
                "message", "没有权限",
                "role", manager.getRole()
            ));
        }
        
        // Get employees created by this manager
        List<User> employees = userDao.findByCreatedBy(managerId);
        
        log.info("Found {} employees for manager {}", employees.size(), managerId);
        
        // Log employee details for debugging
        for (User emp : employees) {
            log.info("  Employee: id={}, username={}, role={}, createdBy={}", 
                emp.getId(), emp.getUsername(), emp.getRole(), emp.getCreatedBy());
        }
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "employees", employees,
            "count", employees.size()
        ));
    }
    
    /**
     * Delete employee account
     */
    @DeleteMapping("/employees/{employeeId}")
    public ResponseEntity<?> deleteEmployee(
        @PathVariable String employeeId,
        @RequestParam String managerId
    ) {
        log.info("Deleting employee: employeeId={}, managerId={}", employeeId, managerId);
        
        // Check permission
        if (!permissionService.canCreateEmployees(managerId)) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "Permission denied",
                "message", "没有权限"
            ));
        }
        
        // Verify employee exists and was created by this manager
        User employee = userDao.findById(employeeId);
        if (employee == null) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "Employee not found",
                "message", "员工不存在"
            ));
        }
        
        if (!managerId.equals(employee.getCreatedBy())) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "Permission denied",
                "message", "只能删除自己创建的员工账号"
            ));
        }
        
        // Delete employee
        userDao.delete(employeeId);
        
        log.info("Employee deleted: id={}, username={}", employeeId, employee.getUsername());
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "员工账号已删除"
        ));
    }
    
    /**
     * Get employee details
     */
    @GetMapping("/employees/{employeeId}")
    public ResponseEntity<?> getEmployee(
        @PathVariable String employeeId,
        @RequestParam String managerId
    ) {
        // Check permission
        if (!permissionService.canCreateEmployees(managerId)) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "Permission denied"
            ));
        }
        
        User employee = userDao.findById(employeeId);
        if (employee == null) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "Employee not found"
            ));
        }
        
        // Verify employee was created by this manager
        if (!managerId.equals(employee.getCreatedBy())) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "Permission denied"
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "employee", employee
        ));
    }
    
    /**
     * Login endpoint - authenticate user and return role information
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        log.info("Login attempt for username: {}", request.getUsername());
        log.info("Password provided (length): {}", request.getPassword() != null ? request.getPassword().length() : 0);
        
        // Check if user exists first
        User existingUser = userDao.findByUsername(request.getUsername());
        if (existingUser != null) {
            log.info("User found: id={}, role={}", existingUser.getId(), existingUser.getRole());
            log.info("Stored password starts with: {}", 
                existingUser.getPassword() != null ? existingUser.getPassword().substring(0, Math.min(10, existingUser.getPassword().length())) : "null");
        } else {
            log.warn("User not found: {}", request.getUsername());
        }
        
        User user = userDao.authenticateUser(request.getUsername(), request.getPassword());
        
        if (user == null) {
            log.warn("Login failed for username: {} - authentication returned null", request.getUsername());
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "error", "用户名或密码错误"
            ));
        }
        
        log.info("Login successful for user: {} (role: {})", user.getId(), user.getRole());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("userId", user.getId());
        response.put("username", user.getUsername());
        response.put("role", user.getRole()); // Include role in response
        response.put("email", user.getEmail());
        
        return ResponseEntity.ok(response);
    }
}

/**
 * Request DTO for creating employee
 */
class CreateEmployeeRequest {
    private String username;
    private String password;
    private String email;
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}

/**
 * Request DTO for login
 */
class LoginRequest {
    private String username;
    private String password;
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
