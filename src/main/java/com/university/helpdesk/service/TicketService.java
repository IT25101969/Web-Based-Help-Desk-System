package com.university.helpdesk.service;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;

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
            NotificationService notificationService
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
            throw new SecurityException("You cannot access this ticket.");
        }

        return ticket;
    }

    @Transactional(readOnly = true)
    public List<Ticket> getSupportTickets(String universityId) {
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

        return ticketRepository
                .findByCategoryDepartmentDepartmentIdInOrderByCreatedDateDesc(departmentIds);
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

    @Transactional
    public Ticket changeStatus(
            Long ticketId,
            TicketStatus newStatus,
            String changedByUniversityId,
            String reason
    ) {
        Ticket ticket = getTicket(ticketId);
        UserAccount actor = userAccountRepository.findByUniversityId(changedByUniversityId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));

        TicketStatus oldStatus = ticket.getStatus();

        if (oldStatus == newStatus) {
            return ticket;
        }

        ticket.setStatus(newStatus);

        if (newStatus == TicketStatus.RESOLVED) {
            ticket.setResolvedDate(LocalDateTime.now());
        }

        if (newStatus == TicketStatus.CLOSED) {
            ticket.setClosedDate(LocalDateTime.now());
        }

        ticketRepository.save(ticket);

        TicketStatusHistory history = new TicketStatusHistory();
        history.setTicket(ticket);
        history.setChangedByUser(actor);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setReason(blankToNull(reason));
        historyRepository.save(history);

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
            Department department = ticket.getCategory() != null ? ticket.getCategory().getDepartment() : null;
            if (department != null) {
                List<UserDepartment> memberships = userDepartmentRepository
                        .findByDepartmentDepartmentIdAndActiveTrue(department.getDepartmentId());
                for (UserDepartment member : memberships) {
                    if (!member.getUser().getUserId().equals(actor.getUserId())) {
                        notificationService.notifyUser(
                                member.getUser(),
                                ticket,
                                NotificationType.ESCALATED,
                                "Ticket " + ticket.getReferenceNo() + " has been escalated in " + department.getDepartmentName() + "."
                        );
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
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String random = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
        return "HD-" + date + "-" + random;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
