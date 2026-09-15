package com.university.helpdesk.dto;

import java.util.Map;

public record ReportSummary(
        long totalTickets,
        long newTickets,
        long assignedTickets,
        long inProgressTickets,
        long escalatedTickets,
        long resolvedTickets,
        long closedTickets,
        double resolutionRate,
        double averageResponseHours,
        double averageResolutionHours,
        Map<String, Long> departmentVolumes,
        Map<String, Long> activeWorkloadByStaff
) {
}
