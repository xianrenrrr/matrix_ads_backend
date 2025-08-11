package com.example.demo.util;

import com.example.demo.model.Invite;
import java.time.Clock;
import java.util.Date;

/**
 * Utility class for centralizing invite/group validity logic
 */
public class InviteValidity {
    
    /**
     * Check if an invite/group is active using system clock
     * @param invite the invite to check
     * @return true if active, false otherwise
     */
    public static boolean isActive(Invite invite) {
        return isActive(invite, Clock.systemUTC());
    }
    
    /**
     * Check if an invite/group is active using provided clock (for testing)
     * @param invite the invite to check
     * @param clock the clock to use for current time
     * @return true if active, false otherwise
     */
    public static boolean isActive(Invite invite, Clock clock) {
        if (invite == null) {
            return false;
        }
        
        // Must have active status
        if (!"active".equals(invite.getStatus())) {
            return false;
        }
        
        // Permanent group (no expiration)
        if (invite.getExpiresAt() == null) {
            return true; // Only status matters for permanent groups
        }
        
        // Temporary invite - check expiration
        Date now = Date.from(clock.instant());
        return now.before(invite.getExpiresAt());
    }
}