package com.example.demo.constants;

/**
 * User role constants for role-based access control (RBAC)
 */
public class UserRole {
    
    // Role constants - using lowercase with underscore to match existing data
    public static final String CONTENT_MANAGER = "content_manager";
    public static final String EMPLOYEE = "employee";
    
    /**
     * Check if role is content manager
     */
    public static boolean isContentManager(String role) {
        return CONTENT_MANAGER.equals(role);
    }
    
    /**
     * Check if role is employee
     */
    public static boolean isEmployee(String role) {
        return EMPLOYEE.equals(role);
    }
    
    /**
     * Validate role
     */
    public static boolean isValidRole(String role) {
        return CONTENT_MANAGER.equals(role) || EMPLOYEE.equals(role);
    }
}
