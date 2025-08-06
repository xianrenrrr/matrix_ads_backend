package com.example.demo.service;

import java.util.List;
import java.util.Map;

public interface TemplateSubscriptionService {
    
    /**
     * Subscribe a template to multiple groups
     * @param templateId The template ID to subscribe
     * @param groupIds List of group IDs
     * @return Subscription result with statistics
     */
    SubscriptionResult batchSubscribeToTemplate(String templateId, List<String> groupIds);
    
    /**
     * Unsubscribe a template from multiple groups
     * @param templateId The template ID to unsubscribe
     * @param groupIds List of group IDs
     * @return Unsubscription result with statistics
     */
    SubscriptionResult batchUnsubscribeFromTemplate(String templateId, List<String> groupIds);
    
    /**
     * Result of batch subscription operation
     */
    class SubscriptionResult {
        private final int totalUsersAffected;
        private final List<String> processedGroups;
        private final Map<String, Integer> groupMemberCounts;
        private final List<String> failedGroups;
        
        public SubscriptionResult(int totalUsersAffected, List<String> processedGroups, 
                                Map<String, Integer> groupMemberCounts, List<String> failedGroups) {
            this.totalUsersAffected = totalUsersAffected;
            this.processedGroups = processedGroups;
            this.groupMemberCounts = groupMemberCounts;
            this.failedGroups = failedGroups;
        }
        
        public int getTotalUsersAffected() {
            return totalUsersAffected;
        }
        
        public List<String> getProcessedGroups() {
            return processedGroups;
        }
        
        public Map<String, Integer> getGroupMemberCounts() {
            return groupMemberCounts;
        }
        
        public List<String> getFailedGroups() {
            return failedGroups;
        }
        
        public boolean hasFailures() {
            return failedGroups != null && !failedGroups.isEmpty();
        }
    }
}