package com.example.demo.scheduler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Workflow Scheduler
 * Handles scheduled tasks for workflow automation
 */
@Component
@Profile("!ci") // Disable scheduler during CI tests
public class WorkflowScheduler {
    
}