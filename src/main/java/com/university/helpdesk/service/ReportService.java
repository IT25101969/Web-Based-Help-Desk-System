package com.university.helpdesk.service;

import com.university.helpdesk.dto.ReportSummary;
import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.TicketAssignmentRepository;
import com.university.helpdesk.repository.TicketRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final TicketRepository ticketRepository;
    private final TicketAssignmentRepository assignmentRepository;

    public ReportService(
            TicketRepository ticketRepository,
            TicketAssignmentRepository assignmentRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional(readOnly = true)
    public ReportSummary buildSummary(
            LocalDate startDate,
            LocalDate endDate,
            Long departmentId
    ) {
        List<Ticket> tickets = filteredTickets(startDate, endDate, departmentId);

        Map<Long, LocalDateTime> firstAssignment = assignmentRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getTicket().getTicketId(),
                        TicketAssignment::getAssignedDate,
                        (a, b) -> a.isBefore(b) ? a : b
                ));

        long newCount = countStatus(tickets, TicketStatus.NEW);
        long assigned = countStatus(tickets, TicketStatus.ASSIGNED);
        long inProgress = countStatus(tickets, TicketStatus.IN_PROGRESS);
        long escalated = countStatus(tickets, TicketStatus.ESCALATED);
        long resolved = countStatus(tickets, TicketStatus.RESOLVED);
        long closed = countStatus(tickets, TicketStatus.CLOSED);

        long completed = resolved + closed;
        double resolutionRate = tickets.isEmpty()
                ? 0.0
                : (completed * 100.0) / tickets.size();

        double averageResponseHours = tickets.stream()
                .filter(t -> firstAssignment.containsKey(t.getTicketId()))
                .mapToLong(t -> Duration.between(
                        t.getCreatedDate(),
                        firstAssignment.get(t.getTicketId())
                ).toMinutes())
                .average()
                .orElse(0.0) / 60.0;

        double averageResolutionHours = tickets.stream()
                .filter(t -> t.getResolvedDate() != null)
                .mapToLong(t -> Duration.between(
                        t.getCreatedDate(),
                        t.getResolvedDate()
                ).toMinutes())
                .average()
                .orElse(0.0) / 60.0;

        Map<String, Long> departmentVolumes = tickets.stream()
                .collect(Collectors.groupingBy(
                        ticket -> ticket.getCategory().getDepartment() == null
                                ? "Manual Routing"
                                : ticket.getCategory().getDepartment().getDepartmentName(),
                        TreeMap::new,
                        Collectors.counting()
                ));

        Set<Long> filteredTicketIds = tickets.stream()
                .map(Ticket::getTicketId)
                .collect(Collectors.toSet());

        Map<String, Long> workload = assignmentRepository.findAll()
                .stream()
                .filter(a -> a.getStatus() == AssignmentStatus.ACTIVE)
                .filter(a -> filteredTicketIds.contains(a.getTicket().getTicketId()))
                .map(TicketAssignment::getAssignedToUser)
                .collect(Collectors.groupingBy(
                        u -> u.getFirstName() + " " + u.getLastName() +
                                " (" + u.getUniversityId() + ")",
                        TreeMap::new,
                        Collectors.counting()
                ));

        return new ReportSummary(
                tickets.size(),
                newCount,
                assigned,
                inProgress,
                escalated,
                resolved,
                closed,
                resolutionRate,
                averageResponseHours,
                averageResolutionHours,
                departmentVolumes,
                workload
        );
    }

    @Transactional(readOnly = true)
    public List<Ticket> filteredTickets(
            LocalDate startDate,
            LocalDate endDate,
            Long departmentId
    ) {
        LocalDateTime start = startDate == null
                ? null
                : startDate.atStartOfDay();

        LocalDateTime endExclusive = endDate == null
                ? null
                : endDate.plusDays(1).atStartOfDay();

        return ticketRepository.findAllByOrderByCreatedDateDesc()
                .stream()
                .filter(ticket -> start == null || !ticket.getCreatedDate().isBefore(start))
                .filter(ticket -> endExclusive == null || ticket.getCreatedDate().isBefore(endExclusive))
                .filter(ticket -> departmentId == null ||
                        (ticket.getCategory().getDepartment() != null &&
                                ticket.getCategory().getDepartment().getDepartmentId().equals(departmentId)))
                .toList();
    }

    private long countStatus(List<Ticket> tickets, TicketStatus status) {
        return tickets.stream()
                .filter(ticket -> ticket.getStatus() == status)
                .count();
    }
}
