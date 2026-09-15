package com.university.helpdesk.service;

import com.university.helpdesk.dto.StaffWorkload;
import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TicketAssignmentService {

    private static final long OVERLOAD_THRESHOLD = 8;

    private final TicketRepository ticketRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserDepartmentRepository userDepartmentRepository;
    private final UserRoleRepository userRoleRepository;
    private final TicketAssignmentRepository assignmentRepository;
    private final TicketStatusHistoryRepository historyRepository;
    private final NotificationService notificationService;

    public TicketAssignmentService(
            TicketRepository ticketRepository,
            UserAccountRepository userAccountRepository,
            UserDepartmentRepository userDepartmentRepository,
            UserRoleRepository userRoleRepository,
            TicketAssignmentRepository assignmentRepository,
            TicketStatusHistoryRepository historyRepository,
            NotificationService notificationService
    ) {
        this.ticketRepository = ticketRepository;
        this.userAccountRepository = userAccountRepository;
        this.userDepartmentRepository = userDepartmentRepository;
        this.userRoleRepository = userRoleRepository;
        this.assignmentRepository = assignmentRepository;
        this.historyRepository = historyRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<StaffWorkload> getCandidates(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket was not found."));

        Department department = ticket.getCategory().getDepartment();
        if (department == null) {
            return List.of();
        }

        Map<Long, StaffWorkload> results = new LinkedHashMap<>();

        for (UserDepartment membership :
                userDepartmentRepository.findByDepartmentDepartmentIdAndActiveTrue(
                        department.getDepartmentId()
                )) {

            UserAccount user = membership.getUser();

            if (user.getAccountStatus() != AccountStatus.ACTIVE || !isSupportUser(user.getUserId())) {
                continue;
            }

            long workload = assignmentRepository
                    .countByAssignedToUserUserIdAndStatus(
                            user.getUserId(),
                            AssignmentStatus.ACTIVE
                    );

            results.put(
                    user.getUserId(),
                    new StaffWorkload(
                            user.getUserId(),
                            user.getUniversityId(),
                            user.getFirstName() + " " + user.getLastName(),
                            workload,
                            workload >= OVERLOAD_THRESHOLD
                    )
            );
        }

        return new ArrayList<>(results.values());
    }

    @Transactional
    public TicketAssignment assign(
            Long ticketId,
            Long assignedToUserId,
            String assignedByUniversityId,
            TicketPriority priority
    ) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket was not found."));

        UserAccount assignedTo = userAccountRepository.findById(assignedToUserId)
                .orElseThrow(() -> new IllegalArgumentException("Selected staff member was not found."));

        UserAccount assignedBy = userAccountRepository.findByUniversityId(assignedByUniversityId)
                .orElseThrow(() -> new IllegalArgumentException("Assigning user was not found."));

        if (assignedTo.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Inactive or disabled staff cannot be assigned.");
        }

        if (!isSupportUser(assignedTo.getUserId())) {
            throw new IllegalArgumentException("Selected user does not have a support role.");
        }

        Department department = ticket.getCategory().getDepartment();

        if (department != null &&
                !userDepartmentRepository.existsByUserUserIdAndDepartmentDepartmentIdAndActiveTrue(
                        assignedTo.getUserId(),
                        department.getDepartmentId()
                )) {
            throw new IllegalArgumentException("Selected staff member does not belong to the ticket department.");
        }

        assignmentRepository
                .findFirstByTicketTicketIdAndStatusOrderByAssignedDateDesc(
                        ticketId,
                        AssignmentStatus.ACTIVE
                )
                .ifPresent(existing -> {
                    existing.setStatus(AssignmentStatus.REASSIGNED);
                    existing.setEndDate(LocalDateTime.now());
                    assignmentRepository.save(existing);

                    if (!existing.getAssignedToUser().getUserId().equals(assignedTo.getUserId())) {
                        notificationService.notifyUser(
                                existing.getAssignedToUser(),
                                ticket,
                                NotificationType.ASSIGNED,
                                "Ticket " + ticket.getReferenceNo() + " was reassigned to " +
                                        assignedTo.getFirstName() + " " + assignedTo.getLastName() + "."
                        );
                    }
                });

        TicketStatus oldStatus = ticket.getStatus();

        if (priority != null) {
            ticket.setPriority(priority);
        }
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticketRepository.save(ticket);

        TicketAssignment assignment = new TicketAssignment();
        assignment.setTicket(ticket);
        assignment.setAssignedToUser(assignedTo);
        assignment.setAssignedByUser(assignedBy);
        assignment.setStatus(AssignmentStatus.ACTIVE);
        assignment = assignmentRepository.save(assignment);

        if (oldStatus != TicketStatus.ASSIGNED) {
            TicketStatusHistory history = new TicketStatusHistory();
            history.setTicket(ticket);
            history.setChangedByUser(assignedBy);
            history.setOldStatus(oldStatus);
            history.setNewStatus(TicketStatus.ASSIGNED);
            history.setReason("Ticket assigned to " + assignedTo.getUniversityId() + ".");
            historyRepository.save(history);
        }

        notificationService.notifyUser(
                assignedTo,
                ticket,
                NotificationType.ASSIGNED,
                "Ticket " + ticket.getReferenceNo() + " was assigned to you."
        );

        notificationService.notifyUser(
                ticket.getStudent().getUser(),
                ticket,
                NotificationType.ASSIGNED,
                "Your ticket " + ticket.getReferenceNo() + " has been assigned to support staff."
        );

        return assignment;
    }

    @Transactional(readOnly = true)
    public List<TicketAssignment> getHistory(Long ticketId) {
        return assignmentRepository.findByTicketTicketIdOrderByAssignedDateDesc(ticketId);
    }

    private boolean isSupportUser(Long userId) {
        return userRoleRepository.findByUserUserIdAndActiveTrue(userId)
                .stream()
                .map(userRole -> userRole.getRole().getRoleName())
                .anyMatch(role -> role.equals("Help Desk Support Staff")
                        || role.equals("Department Support Team Member")
                        || role.equals("Department Manager")
                        || role.equals("System Administrator"));
    }
}
