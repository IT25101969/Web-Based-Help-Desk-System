package com.university.helpdesk.service;

import com.university.helpdesk.entity.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.*;

@Service("ticketDeadlines")
public class TicketDeadlineService {
    private final int low, medium, high, critical;
    public TicketDeadlineService(@Value("${app.sla.low-hours:120}") int low,
            @Value("${app.sla.medium-hours:72}") int medium,
            @Value("${app.sla.high-hours:24}") int high,
            @Value("${app.sla.critical-hours:4}") int critical) {
        this.low = Math.max(1, low); this.medium = Math.max(1, medium);
        this.high = Math.max(1, high); this.critical = Math.max(1, critical);
    }
    public LocalDateTime dueAt(Ticket ticket) {
        return ticket.getCreatedDate().plusHours(switch (ticket.getPriority()) {
            case LOW -> low; case MEDIUM -> medium; case HIGH -> high; case CRITICAL -> critical;
        });
    }
    public String state(Ticket ticket) {
        if (ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.CLOSED) return "complete";
        return stateAt(ticket, LocalDateTime.now());
    }
    public String stateAt(Ticket ticket, LocalDateTime now) {
        if (ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.CLOSED) return "complete";
        if (!dueAt(ticket).isAfter(now)) return "overdue";
        return dueAt(ticket).isBefore(now.plusHours(2)) ? "soon" : "on-track";
    }
    public String label(Ticket ticket) {
        return switch (state(ticket)) { case "complete" -> "Completed"; case "overdue" -> "Overdue";
            case "soon" -> "Due soon"; default -> "On track"; };
    }
}
