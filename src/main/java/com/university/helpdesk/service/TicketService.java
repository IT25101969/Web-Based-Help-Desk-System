package com.university.helpdesk.service;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final CategoryRepository categoryRepository;
    private final StudentRepository studentRepository;
    private final UserAccountRepository userAccountRepository;
    private final IncidentRepository incidentRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final TicketStatusHistoryRepository historyRepository;
    private final UserDepartmentRepository userDepartmentRepository;
    private final UserRoleRepository userRoleRepository;
    private final NotificationService notificationService;
    private final TicketAssignmentRepository assignmentRepository;
    private final ActivityLogService activityLogService;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentService attachmentService;
    private final UserCommentRepository userCommentRepository;
    private final FeedbackRepository feedbackRepository;
    private final NotificationRepository notificationRepository;
    private final EmailNotificationQueueRepository emailQueueRepository;

    public TicketService(
            TicketRepository ticketRepository,
            CategoryRepository categoryRepository,
            StudentRepository studentRepository,
            UserAccountRepository userAccountRepository,
            IncidentRepository incidentRepository,
            ServiceRequestRepository serviceRequestRepository,
            TicketStatusHistoryRepository historyRepository,
            UserDepartmentRepository userDepartmentRepository,
            UserRoleRepository userRoleRepository,
            NotificationService notificationService,
            TicketAssignmentRepository assignmentRepository,
            ActivityLogService activityLogService,
            AttachmentRepository attachmentRepository,
            AttachmentService attachmentService,
            UserCommentRepository userCommentRepository,
            FeedbackRepository feedbackRepository,
            NotificationRepository notificationRepository,
            EmailNotificationQueueRepository emailQueueRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.studentRepository = studentRepository;
        this.userAccountRepository = userAccountRepository;
        this.incidentRepository = incidentRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.historyRepository = historyRepository;
        this.userDepartmentRepository = userDepartmentRepository;
        this.userRoleRepository = userRoleRepository;
        this.notificationService = notificationService;
        this.assignmentRepository = assignmentRepository;
        this.activityLogService = activityLogService;
        this.attachmentRepository = attachmentRepository;
        this.attachmentService = attachmentService;
        this.userCommentRepository = userCommentRepository;
        this.feedbackRepository = feedbackRepository;
        this.notificationRepository = notificationRepository;
        this.emailQueueRepository = emailQueueRepository;
    }

    @Transactional(rollbackFor = java.io.IOException.class)
    public Ticket createTicketWithAttachment(String universityId, Long categoryId, TicketType ticketType,
            String subject, String description, String subtypeDetail, String severity,
            org.springframework.web.multipart.MultipartFile attachment) throws java.io.IOException {
        Ticket ticket = createTicket(universityId, categoryId, ticketType, subject, description, subtypeDetail, severity);
        attachmentService.store(ticket, attachment);
        return ticket;
    }

    @Transactional
    public Ticket createTicket(
            String universityId,
            Long categoryId,
            TicketType ticketType,
            String subject,
            String description,
            String subtypeDetail,
            String severity
    ) {
        if (categoryId == null) {
            throw new IllegalArgumentException("Please select a category.");
        }
        if (ticketType == null) {
            throw new IllegalArgumentException("Please select a ticket type.");
        }
        if (subject == null || subject.trim().isBlank()) {
            throw new IllegalArgumentException("Subject is required.");
        }
        if (subject.trim().length() > 200) {
            throw new IllegalArgumentException("Subject cannot exceed 200 characters.");
        }
        if (description == null || description.trim().isBlank()) {
            throw new IllegalArgumentException("Description is required.");
        }

        UserAccount user = userAccountRepository.findByUniversityId(universityId)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user was not found."));

        Student student = studentRepository.findById(user.getUserId())
                .orElseThrow(() -> new IllegalStateException("Student profile was not found."));

        Category category = categoryRepository.findById(categoryId)
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Please select a valid active category."));

        Ticket ticket = new Ticket();
        ticket.setReferenceNo(generateReference());
        ticket.setStudent(student);
        ticket.setCategory(category);
        ticket.setTicketType(ticketType);
        ticket.setSubject(subject.trim());
        ticket.setDescription(description.trim());
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setStatus(TicketStatus.NEW);
        ticket = ticketRepository.save(ticket);

        if (ticketType == TicketType.INCIDENT) {
            Incident incident = new Incident();
            incident.setTicket(ticket);
            incident.setIncidentType(blankToNull(subtypeDetail));
            incident.setSeverity(blankToNull(severity));
            incidentRepository.save(incident);
        } else {
            ServiceRequest request = new ServiceRequest();
            request.setTicket(ticket);
            request.setRequestedService(blankToNull(subtypeDetail));
            request.setRequestedDate(LocalDate.now());
            serviceRequestRepository.save(request);
        }

        TicketStatusHistory history = new TicketStatusHistory();
        history.setTicket(ticket);
        history.setChangedByUser(user);
        history.setOldStatus(null);
        history.setNewStatus(TicketStatus.NEW);
        history.setReason("Ticket submitted by student.");
        historyRepository.save(history);

        notificationService.notifyUser(
                user,
                ticket,
                NotificationType.SUBMITTED,
                "Your ticket " + ticket.getReferenceNo() + " was submitted successfully."
        );

        notifyDestinationQueue(ticket);
        activityLogService.log(user, "TICKET_SUBMITTED", "TICKET", ticket.getTicketId(), null);

        return ticket;
    }

    @Transactional(readOnly = true)
    public List<Ticket> getStudentTickets(String universityId) {
        UserAccount user = userAccountRepository.findByUniversityId(universityId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));
        return ticketRepository.findByStudentUserIdOrderByCreatedDateDesc(user.getUserId());
    }

    @Transactional(readOnly = true)
    public Ticket getStudentTicket(Long ticketId, String universityId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket was not found."));

        if (!ticket.getStudent().getUser().getUniversityId().equals(universityId)) {
            throw new org.springframework.security.access.AccessDeniedException("You cannot access this ticket.");
        }

        return ticket;
    }

    @Transactional(readOnly = true)
    public Incident getIncident(Long ticketId) {
        return incidentRepository.findById(ticketId).orElse(null);
    }

    @Transactional(readOnly = true)
    public ServiceRequest getServiceRequest(Long ticketId) {
        return serviceRequestRepository.findById(ticketId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Ticket getAuthorizedSupportTicket(Long ticketId, String universityId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket was not found."));

        UserAccount user = userAccountRepository.findByUniversityId(universityId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new org.springframework.security.access.AccessDeniedException("Account is inactive.");
        }

        List<UserRole> activeRoles = userRoleRepository.findByUserUserIdAndActiveTrue(user.getUserId());
        boolean isAdmin = activeRoles.stream()
                .anyMatch(r -> "System Administrator".equals(r.getRole().getRoleName()));

        if (isAdmin) {
            return ticket;
        }

        Department department = ticket.getCategory() != null ? ticket.getCategory().getDepartment() : null;
        if (department == null) {
            throw new org.springframework.security.access.AccessDeniedException("This ticket has no mapped department and requires manual routing by a System Administrator.");
        }

        boolean isSupport = activeRoles.stream()
                .anyMatch(r -> {
                    String name = r.getRole().getRoleName();
                    return "Help Desk Support Staff".equals(name)
                            || "Department Support Team Member".equals(name)
                            || "Department Manager".equals(name);
                });

        if (!isSupport) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: insufficient privileges.");
        }

        boolean isMember = userDepartmentRepository.existsByUserUserIdAndDepartmentDepartmentIdAndActiveTrue(
                user.getUserId(),
                department.getDepartmentId()
        );

        if (!isMember) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to access tickets from this department.");
        }

        return ticket;
    }

    @Transactional(readOnly = true)
    public List<Ticket> getSupportTickets(String universityId) {
        return getSupportTicketsWithFilters(universityId, null, null);
    }

    @Transactional(readOnly = true)
    public List<Ticket> getSupportTicketsWithFilters(
            String universityId,
            TicketStatus status,
            TicketPriority priority
    ) {
        UserAccount user = userAccountRepository.findByUniversityId(universityId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));

        List<Long> departmentIds = userDepartmentRepository
                .findByUserUserIdAndActiveTrue(user.getUserId())
                .stream()
                .map(membership -> membership.getDepartment().getDepartmentId())
                .distinct()
                .toList();

        if (departmentIds.isEmpty()) {
            return List.of();
        }

        return ticketRepository.findByDepartmentIdsWithFilters(departmentIds, status, priority);
    }

    @Transactional(readOnly = true)
    public Ticket getTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket was not found."));
    }

    @Transactional(readOnly = true)
    public List<Ticket> getAllTickets() {
        return ticketRepository.findAllByOrderByCreatedDateDesc();
    }

    @Transactional(readOnly = true)
    public List<Ticket> getAdminTicketsWithFilters(
            Long departmentId,
            boolean unmappedOnly,
            TicketStatus status,
            TicketPriority priority
    ) {
        return ticketRepository.findAdminTicketsWithFilters(departmentId, unmappedOnly, status, priority);
    }

    @Transactional
    public Ticket changeStatus(
            Long ticketId,
            TicketStatus newStatus,
            String changedByUniversityId,
            String reason
    ) {
        ticketRepository.findByIdWithLock(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket was not found."));
        Ticket ticket = getAuthorizedSupportTicket(ticketId, changedByUniversityId);
        UserAccount actor = userAccountRepository.findByUniversityId(changedByUniversityId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));

        TicketStatus oldStatus = ticket.getStatus();

        if (oldStatus == newStatus) {
            return ticket;
        }

        boolean valid = switch (oldStatus) {
            case NEW -> newStatus == TicketStatus.ASSIGNED || newStatus == TicketStatus.IN_PROGRESS || newStatus == TicketStatus.ESCALATED;
            case ASSIGNED -> newStatus == TicketStatus.IN_PROGRESS || newStatus == TicketStatus.ESCALATED || newStatus == TicketStatus.RESOLVED;
            case IN_PROGRESS -> newStatus == TicketStatus.ESCALATED || newStatus == TicketStatus.RESOLVED;
            case ESCALATED -> newStatus == TicketStatus.ASSIGNED || newStatus == TicketStatus.IN_PROGRESS || newStatus == TicketStatus.RESOLVED;
            case RESOLVED -> newStatus == TicketStatus.CLOSED || newStatus == TicketStatus.IN_PROGRESS;
            case CLOSED -> false;
        };

        if (!valid) {
            throw new IllegalArgumentException("Invalid status transition from " + oldStatus + " to " + newStatus);
        }

        ticket.setStatus(newStatus);

        if (oldStatus == TicketStatus.RESOLVED && newStatus == TicketStatus.IN_PROGRESS) {
            ticket.setResolvedDate(null);
        } else if (newStatus == TicketStatus.RESOLVED && ticket.getResolvedDate() == null) {
            ticket.setResolvedDate(LocalDateTime.now());
        }

        if (newStatus == TicketStatus.CLOSED) {
            if (ticket.getClosedDate() == null) {
                ticket.setClosedDate(LocalDateTime.now());
            }
            List<TicketAssignment> activeAssignments = assignmentRepository.findByTicketTicketIdAndStatus(ticketId, AssignmentStatus.ACTIVE);
            for (TicketAssignment a : activeAssignments) {
                a.setStatus(AssignmentStatus.COMPLETED);
                a.setEndDate(LocalDateTime.now());
                assignmentRepository.save(a);
            }
        }

        ticketRepository.save(ticket);

        TicketStatusHistory history = new TicketStatusHistory();
        history.setTicket(ticket);
        history.setChangedByUser(actor);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setReason(blankToNull(reason));
        historyRepository.save(history);
        activityLogService.log(actor, "TICKET_STATUS_CHANGED: " + oldStatus + " -> " + newStatus,
                "TICKET", ticketId, null);

        NotificationType type = switch (newStatus) {
            case ASSIGNED -> NotificationType.ASSIGNED;
            case ESCALATED -> NotificationType.ESCALATED;
            case RESOLVED -> NotificationType.RESOLVED;
            case CLOSED -> NotificationType.CLOSED;
            default -> NotificationType.UPDATED;
        };

        notificationService.notifyUser(
                ticket.getStudent().getUser(),
                ticket,
                type,
                "Ticket " + ticket.getReferenceNo() + " status changed to " +
                        newStatus.name().replace('_', ' ').toLowerCase(Locale.ROOT) + "."
        );

        if (newStatus == TicketStatus.ESCALATED) {
            Department dept = ticket.getCategory() != null ? ticket.getCategory().getDepartment() : null;
            if (dept != null) {
                List<UserDepartment> deptMembers = userDepartmentRepository.findByDepartmentDepartmentIdAndActiveTrue(dept.getDepartmentId());
                boolean notifiedManager = false;
                for (UserDepartment ud : deptMembers) {
                    UserAccount u = ud.getUser();
                    boolean isManager = userRoleRepository.findByUserUserIdAndActiveTrue(u.getUserId()).stream()
                            .anyMatch(ur -> "Department Manager".equals(ur.getRole().getRoleName()));
                    if (isManager && !u.getUserId().equals(actor.getUserId())) {
                        notificationService.notifyUser(u, ticket, NotificationType.ESCALATED,
                                "Ticket " + ticket.getReferenceNo() + " in " + dept.getDepartmentName() + " has been escalated.");
                        notifiedManager = true;
                    }
                }
                if (!notifiedManager) {
                    for (UserDepartment ud : deptMembers) {
                        if (!ud.getUser().getUserId().equals(actor.getUserId())) {
                            notificationService.notifyUser(ud.getUser(), ticket, NotificationType.ESCALATED,
                                    "Ticket " + ticket.getReferenceNo() + " in " + dept.getDepartmentName() + " has been escalated.");
                        }
                    }
                }
            } else {
                for (UserRole adminRole : userRoleRepository.findByRoleRoleNameAndActiveTrue("System Administrator")) {
                    if (!adminRole.getUser().getUserId().equals(actor.getUserId())) {
                        notificationService.notifyUser(
                                adminRole.getUser(),
                                ticket,
                                NotificationType.ESCALATED,
                                "Ticket " + ticket.getReferenceNo() + " has been escalated and requires attention."
                        );
                    }
                }
            }
        }

        return ticket;
    }

    @Transactional
    public Ticket updatePriority(
            Long ticketId,
            TicketPriority newPriority,
            String changedByUniversityId
    ) {
        Ticket ticket = getAuthorizedSupportTicket(ticketId, changedByUniversityId);
        UserAccount actor = userAccountRepository.findByUniversityId(changedByUniversityId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));

        if (newPriority == null || newPriority == ticket.getPriority()) {
            return ticket;
        }

        TicketPriority oldPriority = ticket.getPriority();
        ticket.setPriority(newPriority);
        ticketRepository.save(ticket);

        activityLogService.log(
                actor,
                "PRIORITY_CHANGED: " + oldPriority + " -> " + newPriority,
                "TICKET",
                ticket.getTicketId(),
                null
        );

        assignmentRepository.findFirstByTicketTicketIdAndStatusOrderByAssignedDateDesc(ticketId, AssignmentStatus.ACTIVE)
                .ifPresent(active -> {
                    notificationService.notifyUser(
                            active.getAssignedToUser(),
                            ticket,
                            NotificationType.UPDATED,
                            "Ticket " + ticket.getReferenceNo() + " priority changed to " + newPriority + "."
                    );
                });

        return ticket;
    }

    private void notifyDestinationQueue(Ticket ticket) {
        Department department = ticket.getCategory().getDepartment();

        if (department != null) {
            List<UserDepartment> memberships =
                    userDepartmentRepository.findByDepartmentDepartmentIdAndActiveTrue(
                            department.getDepartmentId()
                    );

            for (UserDepartment membership : memberships) {
                notificationService.notifyUser(
                        membership.getUser(),
                        ticket,
                        NotificationType.SUBMITTED,
                        "New ticket " + ticket.getReferenceNo() +
                                " was routed to " + department.getDepartmentName() + "."
                );
            }
            return;
        }

        for (UserRole adminRole :
                userRoleRepository.findByRoleRoleNameAndActiveTrue("System Administrator")) {
            notificationService.notifyUser(
                    adminRole.getUser(),
                    ticket,
                    NotificationType.SYSTEM,
                    "Ticket " + ticket.getReferenceNo() +
                            " has no mapped department and requires manual routing."
            );
        }
    }

    private String generateReference() {
        String reference;
        do {
            String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String random = UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 8)
                    .toUpperCase(Locale.ROOT);
            reference = "HD-" + date + "-" + random;
        } while (ticketRepository.existsByReferenceNo(reference));
        return reference;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Transactional
    public void deleteStudentTicket(Long ticketId, String studentUniversityId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket was not found."));

        if (ticket.getStudent() == null ||
                !ticket.getStudent().getUser().getUniversityId().equals(studentUniversityId)) {
            throw new AccessDeniedException("You are not authorized to delete this ticket.");
        }

        // Clean up physical attachments
        List<Attachment> attachments = attachmentRepository.findByTicketTicketId(ticketId);
        for (Attachment a : attachments) {
            try {
                attachmentService.deleteAttachment(a.getAttachmentId(), ticketId, studentUniversityId);
            } catch (Exception ignored) {
            }
        }

        feedbackRepository.deleteByTicketTicketId(ticketId);
        userCommentRepository.deleteByTicketTicketId(ticketId);
        historyRepository.deleteByTicketTicketId(ticketId);
        assignmentRepository.deleteByTicketTicketId(ticketId);
        emailQueueRepository.disassociateNotificationByTicketId(ticketId);
        emailQueueRepository.deleteByTicketTicketId(ticketId);
        notificationRepository.deleteByTicketTicketId(ticketId);

        incidentRepository.deleteById(ticketId);
        serviceRequestRepository.deleteById(ticketId);

        ticketRepository.delete(ticket);

        activityLogService.log(
                ticket.getStudent().getUser(),
                "TICKET_DELETED",
                "TICKET",
                ticketId,
                null
        );
    }
}
