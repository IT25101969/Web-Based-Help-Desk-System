package com.university.helpdesk.dto;

import com.university.helpdesk.entity.ActivityLog;
import com.university.helpdesk.entity.TicketStatus;

import java.util.List;
import java.util.Map;

public record MonitoringSummary(
        long totalUsers,
        long activeUsers,
        long lockedUsers,
        long disabledUsers,
        long suspiciousUsers,
        Map<TicketStatus, Long> ticketStatusCounts,
        long unresolvedTickets,
        long escalatedTickets,
        Long pendingEmails,
        Long failedEmails,
        List<ActivityLog> recentActivityLogs
) {
}
