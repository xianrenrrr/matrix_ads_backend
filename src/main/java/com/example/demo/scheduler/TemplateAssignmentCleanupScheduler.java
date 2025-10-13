package com.example.demo.scheduler;

import com.example.demo.dao.TemplateAssignmentDao;
import com.example.demo.model.TemplateAssignment;
import com.example.demo.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler for cleaning up expired template assignments
 * and notifying managers about expiring assignments.
 */
@Component
public class TemplateAssignmentCleanupScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(TemplateAssignmentCleanupScheduler.class);
    
    @Autowired
    private TemplateAssignmentDao assignmentDao;
    
    @Autowired(required = false)
    private NotificationService notificationService;
    
    /**
     * Delete expired assignments
     * Runs every day at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredAssignments() {
        try {
            logger.info("Starting cleanup of expired template assignments...");
            
            List<TemplateAssignment> expired = assignmentDao.getExpiredAssignments();
            
            logger.info("Found {} expired assignments to delete", expired.size());
            
            for (TemplateAssignment assignment : expired) {
                try {
                    // Delete expired assignment
                    assignmentDao.deleteAssignment(assignment.getId());
                    
                    // Notify manager if notification service is available
                    if (notificationService != null && assignment.getPushedBy() != null) {
                        notificationService.notifyTemplateExpired(
                            assignment.getPushedBy(),
                            assignment.getGroupId(),
                            assignment.getTemplateSnapshot().getTemplateTitle()
                        );
                    }
                    
                    logger.info("Deleted expired assignment: {} for group: {} (template: {})", 
                        assignment.getId(), 
                        assignment.getGroupId(),
                        assignment.getTemplateSnapshot().getTemplateTitle());
                        
                } catch (Exception e) {
                    logger.error("Failed to delete assignment: " + assignment.getId(), e);
                }
            }
            
            logger.info("Cleanup completed. Deleted {} assignments", expired.size());
            
        } catch (Exception e) {
            logger.error("Error during cleanup of expired assignments", e);
        }
    }
    
    /**
     * Notify managers about assignments expiring soon
     * Runs every day at 9 AM
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void notifyExpiringSoon() {
        try {
            logger.info("Checking for assignments expiring soon...");
            
            // Get assignments expiring within 7 days
            List<TemplateAssignment> expiringSoon = assignmentDao.getExpiringSoonAssignments(7);
            
            logger.info("Found {} assignments expiring within 7 days", expiringSoon.size());
            
            if (notificationService != null) {
                for (TemplateAssignment assignment : expiringSoon) {
                    try {
                        if (assignment.getPushedBy() != null) {
                            notificationService.notifyTemplateExpiringSoon(
                                assignment.getPushedBy(),
                                assignment.getGroupId(),
                                assignment.getTemplateSnapshot().getTemplateTitle(),
                                assignment.getDaysUntilExpiry()
                            );
                            
                            logger.info("Sent expiring notification for assignment: {} (expires in {} days)", 
                                assignment.getId(), 
                                assignment.getDaysUntilExpiry());
                        }
                    } catch (Exception e) {
                        logger.error("Failed to send notification for assignment: " + assignment.getId(), e);
                    }
                }
            } else {
                logger.warn("NotificationService not available, skipping notifications");
            }
            
        } catch (Exception e) {
            logger.error("Error during expiring soon notifications", e);
        }
    }
    
}
