package com.example.demo.service;

import com.example.demo.constants.UserRole;
import com.example.demo.dao.UserDao;
import com.example.demo.model.ManualTemplate;
import com.example.demo.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Permission service for role-based access control (RBAC)
 */
@Service
public class PermissionService {
    
    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);
    
    @Autowired
    private UserDao userDao;
    
    /**
     * Check if user can delete a template
     * 
     * Rules:
     * - Content managers can delete any template
     * - Employees can only delete their own templates within 2 days
     */
    public boolean canDeleteTemplate(String userId, ManualTemplate template) {
        User user = userDao.findById(userId);
        if (user == null) {
            log.warn("User not found: {}", userId);
            return false;
        }
        
        String role = user.getRole();
        
        // Content managers can delete any template
        if (UserRole.isContentManager(role) || "content_manager".equals(role)) {
            log.info("User {} (content manager) can delete template {}", userId, template.getId());
            return true;
        }
        
        // Employees can only delete their own templates
        if (UserRole.isEmployee(role)) {
            // Must be owner
            if (!userId.equals(template.getUserId())) {
                log.warn("Employee {} cannot delete template {} (not owner)", userId, template.getId());
                return false;
            }
            
            // Must be within 2 days
            if (template.getCreatedAt() == null) {
                log.warn("Template {} has no creation date, denying delete", template.getId());
                return false;
            }
            
            long daysSinceCreation = ChronoUnit.DAYS.between(
                template.getCreatedAt().toInstant(),
                Instant.now()
            );
            
            boolean canDelete = daysSinceCreation <= 2;
            log.info("Employee {} {} delete template {} (created {} days ago)", 
                userId, canDelete ? "can" : "cannot", template.getId(), daysSinceCreation);
            
            return canDelete;
        }
        
        log.warn("User {} has unknown role: {}", userId, role);
        return false;
    }
    
    /**
     * Get reason why user cannot delete template (for UI display)
     */
    public String getDeleteDeniedReason(String userId, ManualTemplate template) {
        User user = userDao.findById(userId);
        if (user == null) {
            return "用户不存在";
        }
        
        String role = user.getRole();
        
        if (UserRole.isEmployee(role)) {
            if (!userId.equals(template.getUserId())) {
                return "只能删除自己创建的模板";
            }
            
            if (template.getCreatedAt() != null) {
                long daysSinceCreation = ChronoUnit.DAYS.between(
                    template.getCreatedAt().toInstant(),
                    Instant.now()
                );
                
                if (daysSinceCreation > 2) {
                    return String.format("创建已超过2天（%d天前）", daysSinceCreation);
                }
            }
        }
        
        return "没有权限";
    }
    
    /**
     * Check if user can manage groups
     * 
     * Rules:
     * - Only content managers can manage groups
     */
    public boolean canManageGroups(String userId) {
        User user = userDao.findById(userId);
        if (user == null) {
            return false;
        }
        
        String role = user.getRole();
        boolean canManage = UserRole.isContentManager(role) || "content_manager".equals(role);
        
        log.info("User {} {} manage groups (role: {})", 
            userId, canManage ? "can" : "cannot", role);
        
        return canManage;
    }
    
    /**
     * Check if user can create employee accounts
     * 
     * Rules:
     * - Only content managers can create employee accounts
     */
    public boolean canCreateEmployees(String userId) {
        User user = userDao.findById(userId);
        if (user == null) {
            return false;
        }
        
        String role = user.getRole();
        boolean canCreate = UserRole.isContentManager(role) || "content_manager".equals(role);
        
        log.info("User {} {} create employees (role: {})", 
            userId, canCreate ? "can" : "cannot", role);
        
        return canCreate;
    }
    
    /**
     * Check if user can create templates
     * 
     * Rules:
     * - Both content managers and employees can create templates
     */
    public boolean canCreateTemplates(String userId) {
        User user = userDao.findById(userId);
        if (user == null) {
            return false;
        }
        
        String role = user.getRole();
        return UserRole.isContentManager(role) || 
               "content_manager".equals(role) ||
               UserRole.isEmployee(role);
    }
    
    /**
     * Check if user can push templates
     * 
     * Rules:
     * - Both content managers and employees can push templates
     */
    public boolean canPushTemplates(String userId) {
        User user = userDao.findById(userId);
        if (user == null) {
            return false;
        }
        
        String role = user.getRole();
        return UserRole.isContentManager(role) || 
               "content_manager".equals(role) ||
               UserRole.isEmployee(role);
    }
    
    /**
     * Check if user is content manager
     */
    public boolean isContentManager(String userId) {
        User user = userDao.findById(userId);
        if (user == null) {
            return false;
        }
        
        String role = user.getRole();
        return UserRole.isContentManager(role) || "content_manager".equals(role);
    }
    
    /**
     * Check if user is employee
     */
    public boolean isEmployee(String userId) {
        User user = userDao.findById(userId);
        if (user == null) {
            return false;
        }
        
        return UserRole.isEmployee(user.getRole());
    }
}
