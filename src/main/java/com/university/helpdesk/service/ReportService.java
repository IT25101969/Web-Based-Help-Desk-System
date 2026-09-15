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
    private final com.university.helpdesk.repository.DepartmentRepository departmentRepository;

    public ReportService(
            TicketRepository ticketRepository,
            TicketAssignmentRepository assignmentRepository,
            com.university.helpdesk.repository.DepartmentRepository departmentRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.assignmentRepository = assignmentRepository;
        this.departmentRepository = departmentRepository;
    }

    public Optional<String> validateFilters(LocalDate startDate, LocalDate endDate, Long departmentId) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            return Optional.of("Start date cannot be after end date.");
        }
        if (departmentId != null && !departmentRepository.existsById(departmentId)) {
            return Optional.of("Selected department was not found.");
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public ReportSummary buildSummary(
            LocalDate startDate,
            LocalDate endDate,
            Long departmentId
    ) {
        if (validateFilters(startDate, endDate, departmentId).isPresent()) {
            return emptySummary();
        }

        List<Ticket> tickets = filteredTickets(startDate, endDate, departmentId);

        Set<Long> filteredTicketIds = tickets.stream()
                .map(Ticket::getTicketId)
                .collect(Collectors.toSet());

        Map<Long, LocalDateTime> firstAssignment = new HashMap<>();
        if (!filteredTicketIds.isEmpty()) {
            List<Object[]> rows = assignmentRepository.findFirstAssignmentDatesForTicketIds(filteredTicketIds);
            for (Object[] row : rows) {
                if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                    firstAssignment.put((Long) row[0], (LocalDateTime) row[1]);
                }
            }
        }

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
                        ticket -> ticket.getCategory() == null || ticket.getCategory().getDepartment() == null
                                ? "Manual Routing"
                                : ticket.getCategory().getDepartment().getDepartmentName(),
                        TreeMap::new,
                        Collectors.counting()
                ));

        Map<String, Long> workload = new TreeMap<>();
        if (!filteredTicketIds.isEmpty()) {
            workload = assignmentRepository.findActiveAssignmentsForTicketIds(AssignmentStatus.ACTIVE, filteredTicketIds)
                    .stream()
                    .map(TicketAssignment::getAssignedToUser)
                    .filter(Objects::nonNull)
                    .collect(Collectors.groupingBy(
                            u -> u.getFirstName() + " " + u.getLastName() +
                                    " (" + u.getUniversityId() + ")",
                            TreeMap::new,
                            Collectors.counting()
                    ));
        }

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

    public ReportSummary emptySummary() {
        return new ReportSummary(
                0, 0, 0, 0, 0, 0, 0,
                0.0, 0.0, 0.0,
                Collections.emptyMap(),
                Collections.emptyMap()
        );
    }

    @Transactional(readOnly = true)
    public List<Ticket> filteredTickets(
            LocalDate startDate,
            LocalDate endDate,
            Long departmentId
    ) {
        if (validateFilters(startDate, endDate, departmentId).isPresent()) {
            return Collections.emptyList();
        }

        LocalDateTime start = startDate == null
                ? null
                : startDate.atStartOfDay();

        LocalDateTime endExclusive = endDate == null
                ? null
                : endDate.plusDays(1).atStartOfDay();

        return ticketRepository.findFilteredTickets(start, endExclusive, departmentId);
    }

    private long countStatus(List<Ticket> tickets, TicketStatus status) {
        return tickets.stream()
                .filter(ticket -> ticket.getStatus() == status)
                .count();
    }
}
