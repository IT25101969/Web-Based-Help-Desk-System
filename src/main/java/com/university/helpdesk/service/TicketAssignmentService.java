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
        return getCandidates(ticketId, null);
    }

    @Transactional(readOnly = true)
    public List<StaffWorkload> getCandidates(Long ticketId, String universityId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket was not found."));

        if (universityId != null) {
            UserAccount user = userAccountRepository.findByUniversityId(universityId)
                    .orElseThrow(() -> new IllegalArgumentException("User was not found."));

            List<UserRole> roles = userRoleRepository.findByUserUserIdAndActiveTrue(user.getUserId());
            boolean isAdmin = roles.stream().anyMatch(r -> "System Administrator".equals(r.getRole().getRoleName()));

            if (!isAdmin) {
                Department department = ticket.getCategory() != null ? ticket.getCategory().getDepartment() : null;
                if (department == null) {
                    throw new org.springframework.security.access.AccessDeniedException("This ticket has no mapped department.");
                }
                boolean isMember = userDepartmentRepository.existsByUserUserIdAndDepartmentDepartmentIdAndActiveTrue(
                        user.getUserId(),
                        department.getDepartmentId()
                );
                if (!isMember) {
                    throw new org.springframework.security.access.AccessDeniedException("Not authorized to view candidates for this department.");
                }
            }
        }

        Department department = ticket.getCategory() != null ? ticket.getCategory().getDepartment() : null;
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
        Ticket ticket = ticketRepository.findByIdWithLock(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket was not found."));

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new IllegalArgumentException("Cannot assign or reassign a closed ticket.");
        }

        UserAccount assignedBy = userAccountRepository.findByUniversityId(assignedByUniversityId)
                .orElseThrow(() -> new IllegalArgumentException("Assigning user was not found."));

        if (assignedBy.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new org.springframework.security.access.AccessDeniedException("Assigning user is not active.");
        }

        List<UserRole> actorRoles = userRoleRepository.findByUserUserIdAndActiveTrue(assignedBy.getUserId());
        boolean isAdmin = actorRoles.stream().anyMatch(r -> "System Administrator".equals(r.getRole().getRoleName()));
        boolean isManager = actorRoles.stream().anyMatch(r -> "Department Manager".equals(r.getRole().getRoleName()));
        boolean isHelpDesk = actorRoles.stream().anyMatch(r -> "Help Desk Support Staff".equals(r.getRole().getRoleName()));
        boolean isDeptSupport = actorRoles.stream().anyMatch(r -> "Department Support Team Member".equals(r.getRole().getRoleName()));

        if (!isAdmin && !isManager && !isHelpDesk && !isDeptSupport) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to assign tickets.");
        }

        if (isDeptSupport && !isManager && !isHelpDesk && !isAdmin) {
            throw new org.springframework.security.access.AccessDeniedException("Department Support Team Member does not have assign permission.");
        }

        Department department = ticket.getCategory() != null ? ticket.getCategory().getDepartment() : null;
        if (department == null) {
            throw new IllegalStateException("Ticket requires manual department routing before assignment.");
        }

        if (!isAdmin) {
            boolean isMember = userDepartmentRepository.existsByUserUserIdAndDepartmentDepartmentIdAndActiveTrue(
                    assignedBy.getUserId(),
                    department.getDepartmentId()
            );
            if (!isMember) {
                throw new org.springframework.security.access.AccessDeniedException("You are not authorized to assign tickets for this department.");
            }
        }

        UserAccount assignedTo = userAccountRepository.findById(assignedToUserId)
                .orElseThrow(() -> new IllegalArgumentException("Selected staff member was not found."));

        if (assignedTo.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Inactive or disabled staff cannot be assigned.");
        }

        if (!isSupportUser(assignedTo.getUserId())) {
            throw new IllegalArgumentException("Selected user does not have a support role.");
        }

        if (!userDepartmentRepository.existsByUserUserIdAndDepartmentDepartmentIdAndActiveTrue(
                assignedTo.getUserId(),
                department.getDepartmentId()
        )) {
            throw new IllegalArgumentException("Selected staff member does not belong to the ticket department.");
        }

        List<TicketAssignment> activeAssignments = assignmentRepository
                .findByTicketTicketIdAndStatus(ticketId, AssignmentStatus.ACTIVE);

        UserAccount previousAssignee = null;
        for (TicketAssignment existing : activeAssignments) {
            existing.setStatus(AssignmentStatus.REASSIGNED);
            existing.setEndDate(LocalDateTime.now());
            assignmentRepository.save(existing);
            if (previousAssignee == null) {
                previousAssignee = existing.getAssignedToUser();
            }
        }

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
        } else if (previousAssignee != null && !previousAssignee.getUserId().equals(assignedTo.getUserId())) {
            TicketStatusHistory history = new TicketStatusHistory();
            history.setTicket(ticket);
            history.setChangedByUser(assignedBy);
            history.setOldStatus(oldStatus);
            history.setNewStatus(TicketStatus.ASSIGNED);
            history.setReason("Ticket reassigned from " + previousAssignee.getUniversityId() + " to " + assignedTo.getUniversityId() + ".");
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

        if (previousAssignee != null && !previousAssignee.getUserId().equals(assignedTo.getUserId())) {
            notificationService.notifyUser(
                    previousAssignee,
                    ticket,
                    NotificationType.UPDATED,
                    "Ticket " + ticket.getReferenceNo() + " previously assigned to you was reassigned."
            );
        }

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
