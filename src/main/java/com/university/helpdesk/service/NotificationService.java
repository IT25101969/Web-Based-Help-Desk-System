package com.university.helpdesk.service;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.EmailNotificationQueueRepository;
import com.university.helpdesk.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int DUPLICATE_WINDOW_MINUTES = 2;

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceService preferenceService;
    private final EmailNotificationQueueRepository emailQueueRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationPreferenceService preferenceService,
            EmailNotificationQueueRepository emailQueueRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.preferenceService = preferenceService;
        this.emailQueueRepository = emailQueueRepository;
    }

    @Transactional
    public Notification notifyUser(
            UserAccount user,
            Ticket ticket,
            NotificationType type,
            String message
    ) {
        if (user == null) {
            log.warn("Attempted to notify null user with message: {}", message);
            return null;
        }

        // Duplicate control / consolidation:
        // Prevent duplicate notifications for rapid identical update events within window.
        // Critical lifecycle events (ASSIGNED, ESCALATED, RESOLVED, CLOSED) are NEVER suppressed.
        if (shouldConsolidate(user, ticket, type, message)) {
            log.info("Consolidated duplicate {} notification for user {} on ticket {}",
                    type, user.getUniversityId(), ticket != null ? ticket.getReferenceNo() : "N/A");
            return notificationRepository
                    .findFirstByUserUserIdAndTicketTicketIdAndNotificationTypeAndCreatedDateAfterOrderByCreatedDateDesc(
                            user.getUserId(),
                            ticket.getTicketId(),
                            type,
                            LocalDateTime.now().minusMinutes(DUPLICATE_WINDOW_MINUTES)
                    )
                    .orElse(null);
        }

        NotificationPreference preference = preferenceService.getOrCreatePreference(user.getUserId());
        Notification notification = null;

        // In-App Notification:
        // Delivered if inAppEnabled is true, OR if notification is a mandatory SYSTEM alert
        boolean allowInApp = Boolean.TRUE.equals(preference.getInAppEnabled()) || type == NotificationType.SYSTEM;
        if (allowInApp) {
            notification = new Notification();
            notification.setUser(user);
            notification.setTicket(ticket);
            notification.setNotificationType(type);
            notification.setMessage(message);
            notification.setReadStatus(false);
            notification = notificationRepository.save(notification);
        }

        // Email Notification Queueing:
        // Enqueue email asynchronously if emailEnabled is true and recipient has valid email.
        // Does NOT call SMTP directly.
        boolean allowEmail = Boolean.TRUE.equals(preference.getEmailEnabled());
        if (allowEmail && user.getEmail() != null && !user.getEmail().isBlank()) {
            enqueueEmail(user, ticket, notification, type, message);
        }

        return notification;
    }

    private boolean shouldConsolidate(
            UserAccount user,
            Ticket ticket,
            NotificationType type,
            String message
    ) {
        if (ticket == null || type == null) {
            return false;
        }

        // Never consolidate critical status changes
        if (type == NotificationType.ASSIGNED
                || type == NotificationType.ESCALATED
                || type == NotificationType.RESOLVED
                || type == NotificationType.CLOSED) {
            return false;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(DUPLICATE_WINDOW_MINUTES);
        Optional<Notification> recent = notificationRepository
                .findFirstByUserUserIdAndTicketTicketIdAndNotificationTypeAndCreatedDateAfterOrderByCreatedDateDesc(
                        user.getUserId(),
                        ticket.getTicketId(),
                        type,
                        cutoff
                );

        return recent.isPresent() && message.equals(recent.get().getMessage());
    }

    private void enqueueEmail(
            UserAccount user,
            Ticket ticket,
            Notification notification,
            NotificationType type,
            String message
    ) {
        EmailNotificationQueue queueItem = new EmailNotificationQueue();
        queueItem.setUser(user);
        queueItem.setTicket(ticket);
        queueItem.setNotification(notification);
        queueItem.setRecipientEmail(user.getEmail().trim());

        String refPrefix = ticket != null ? "[" + ticket.getReferenceNo() + "] " : "";
        queueItem.setSubject("Help Desk Alert: " + refPrefix + formatTypeTitle(type));

        StringBuilder body = new StringBuilder();
        body.append("Hello ").append(user.getFirstName()).append(",\n\n");
        body.append(message).append("\n\n");
        if (ticket != null) {
            body.append("Ticket Reference: ").append(ticket.getReferenceNo()).append("\n");
            body.append("Subject: ").append(ticket.getSubject()).append("\n");
            body.append("Status: ").append(ticket.getStatus()).append("\n");
            body.append("Priority: ").append(ticket.getPriority()).append("\n\n");
        }
        body.append("Log in to the University Help Desk system to view details and manage your notifications.\n");
        body.append("\nRegards,\nUniversity Help Desk Support Team");

        queueItem.setMessage(body.toString());
        queueItem.setStatus(EmailQueueStatus.PENDING);
        queueItem.setAttemptCount(0);
        queueItem.setCreatedAt(LocalDateTime.now());

        emailQueueRepository.save(queueItem);
    }

    private String formatTypeTitle(NotificationType type) {
        return switch (type) {
            case SUBMITTED -> "Ticket Submitted";
            case ASSIGNED -> "Ticket Assigned";
            case UPDATED -> "Ticket Updated";
            case ESCALATED -> "Ticket Escalated";
            case RESOLVED -> "Ticket Resolved";
            case CLOSED -> "Ticket Closed";
            case SYSTEM -> "System Notification";
        };
    }

    @Transactional(readOnly = true)
    public List<Notification> getForUser(Long userId) {
        return notificationRepository.findByUserUserIdOrderByCreatedDateDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getUnreadForUser(Long userId) {
        return notificationRepository.findByUserUserIdAndReadStatusFalseOrderByCreatedDateDesc(userId);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByUserUserIdAndReadStatusFalse(userId);
    }

    @Transactional(readOnly = true)
    public Notification getNotificationForUser(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification was not found."));

        if (!notification.getUser().getUserId().equals(userId)) {
            throw new AccessDeniedException("You cannot access this notification.");
        }

        return notification;
    }

    @Transactional
    public void markRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification was not found."));

        if (!notification.getUser().getUserId().equals(userId)) {
            throw new AccessDeniedException("You cannot access this notification.");
        }

        notification.setReadStatus(true);
        notification.setReadDate(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllReadForUser(userId, LocalDateTime.now());
    }

    @Transactional
    public void deleteNotification(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification was not found."));

        if (!notification.getUser().getUserId().equals(userId)) {
            throw new AccessDeniedException("You cannot delete this notification.");
        }

        emailQueueRepository.disassociateNotification(notificationId);
        notificationRepository.delete(notification);
    }

    @Transactional
    public int clearAllForUser(Long userId) {
        emailQueueRepository.disassociateAllNotificationsForUser(userId);
        return notificationRepository.deleteAllForUser(userId);
    }
}
