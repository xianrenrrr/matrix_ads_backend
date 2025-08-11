package com.example.demo.service;

import com.example.demo.dao.UserDao;
import com.example.demo.dao.InviteDao;
import com.example.demo.model.Invite;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TemplateSubscriptionServiceImpl implements TemplateSubscriptionService {
    
    @Autowired
    private InviteDao inviteDao; // Using InviteDao instead of GroupDao
    
    @Autowired
    private UserDao userDao;
    
    @Override
    public SubscriptionResult batchSubscribeToTemplate(String templateId, List<String> groupIds) {
        int totalUsersSubscribed = 0;
        List<String> processedGroups = new ArrayList<>();
        List<String> failedGroups = new ArrayList<>();
        Map<String, Integer> groupMemberCounts = new HashMap<>();
        
        System.out.printf("Starting batch subscription: template %s to %d groups%n", templateId, groupIds.size());
        
        for (String groupId : groupIds) {
            try {
                String cleanGroupId = groupId.trim();
                Invite group = inviteDao.findById(cleanGroupId); // Now using Invite as Group
                
                if (group != null && group.getMemberIds() != null && !group.getMemberIds().isEmpty()) {
                    int groupMemberCount = 0;
                    
                    // Subscribe all members of this group to the template
                    for (String memberId : group.getMemberIds()) {
                        try {
                            userDao.addSubscribedTemplate(memberId, templateId);
                            groupMemberCount++;
                            totalUsersSubscribed++;
                        } catch (Exception e) {
                            System.err.printf("Failed to subscribe user %s to template %s: %s%n", 
                                memberId, templateId, e.getMessage());
                        }
                    }
                    
                    processedGroups.add(group.getGroupName());
                    groupMemberCounts.put(group.getGroupName(), groupMemberCount);
                    
                    System.out.printf("Subscribed template %s to %d users in group '%s'%n", 
                        templateId, groupMemberCount, group.getGroupName());
                    
                } else {
                    String errorMsg = cleanGroupId + " (group not found or has no members)";
                    failedGroups.add(errorMsg);
                    System.err.printf("Failed to process group %s: %s%n", cleanGroupId, errorMsg);
                }
            } catch (Exception e) {
                String errorMsg = groupId + " (processing error: " + e.getMessage() + ")";
                failedGroups.add(errorMsg);
                System.err.printf("Error processing group %s: %s%n", groupId, e.getMessage());
            }
        }
        
        System.out.printf("Batch subscription completed: %d users across %d groups%n", 
            totalUsersSubscribed, processedGroups.size());
        
        return new SubscriptionResult(totalUsersSubscribed, processedGroups, groupMemberCounts, failedGroups);
    }
    
    @Override
    public SubscriptionResult batchUnsubscribeFromTemplate(String templateId, List<String> groupIds) {
        int totalUsersUnsubscribed = 0;
        List<String> processedGroups = new ArrayList<>();
        List<String> failedGroups = new ArrayList<>();
        Map<String, Integer> groupMemberCounts = new HashMap<>();
        
        System.out.printf("Starting batch unsubscription: template %s from %d groups%n", templateId, groupIds.size());
        
        for (String groupId : groupIds) {
            try {
                String cleanGroupId = groupId.trim();
                Invite group = inviteDao.findById(cleanGroupId); // Now using Invite as Group
                
                if (group != null && group.getMemberIds() != null) {
                    int groupMemberCount = 0;
                    
                    // Unsubscribe all members of this group from the template
                    for (String memberId : group.getMemberIds()) {
                        try {
                            userDao.removeSubscribedTemplate(memberId, templateId);
                            groupMemberCount++;
                            totalUsersUnsubscribed++;
                        } catch (Exception e) {
                            System.err.printf("Failed to unsubscribe user %s from template %s: %s%n", 
                                memberId, templateId, e.getMessage());
                        }
                    }
                    
                    processedGroups.add(group.getGroupName());
                    groupMemberCounts.put(group.getGroupName(), groupMemberCount);
                    
                } else {
                    String errorMsg = cleanGroupId + " (group not found)";
                    failedGroups.add(errorMsg);
                }
            } catch (Exception e) {
                String errorMsg = groupId + " (processing error: " + e.getMessage() + ")";
                failedGroups.add(errorMsg);
                System.err.printf("Error processing group %s: %s%n", groupId, e.getMessage());
            }
        }
        
        System.out.printf("Batch unsubscription completed: %d users across %d groups%n", 
            totalUsersUnsubscribed, processedGroups.size());
        
        return new SubscriptionResult(totalUsersUnsubscribed, processedGroups, groupMemberCounts, failedGroups);
    }
}