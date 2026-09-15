package com.university.helpdesk;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class NotificationServiceTests {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationPreferenceService preferenceService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private EmailNotificationQueueRepository emailQueueRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketAssignmentService ticketAssignmentService;

    private UserAccount testUser1;
    private UserAccount testUser2;
    private Ticket testTicket;

    @BeforeEach
    void setUp() {
        String uid1 = "TEST_U1_" + UUID.randomUUID().toString().substring(0, 6);
        String uid2 = "TEST_U2_" + UUID.randomUUID().toString().substring(0, 6);

        testUser1 = new UserAccount();
        testUser1.setUniversityId(uid1);
        testUser1.setEmail(uid1.toLowerCase() + "@university.edu");
        testUser1.setFirstName("Alice");
        testUser1.setLastName("Tester");
        testUser1.setPasswordHash("hash123");
        testUser1.setAccountStatus(AccountStatus.ACTIVE);
        testUser1 = userAccountRepository.save(testUser1);

        testUser2 = new UserAccount();
        testUser2.setUniversityId(uid2);
        testUser2.setEmail(uid2.toLowerCase() + "@university.edu");
        testUser2.setFirstName("Bob");
        testUser2.setLastName("Tester");
        testUser2.setPasswordHash("hash123");
        testUser2.setAccountStatus(AccountStatus.ACTIVE);
        testUser2 = userAccountRepository.save(testUser2);

        Student student = new Student();
        student.setUser(testUser1);
        student.setFaculty("Science");
        student.setProgram("Computer Science");
        student.setAcademicYear(2);
        studentRepository.save(student);

        Category category = categoryRepository.findAll().stream()
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .findFirst()
                .orElseGet(() -> {
                    Category c = new Category();
                    c.setCategoryName("General Support " + UUID.randomUUID().toString().substring(0, 5));
                    c.setStatus("ACTIVE");
                    return categoryRepository.save(c);
                });

        testTicket = new Ticket();
        testTicket.setReferenceNo("HD-TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        testTicket.setStudent(student);
        testTicket.setCategory(category);
        testTicket.setSubject("Test issue");
        testTicket.setDescription("Description of test issue");
        testTicket.setStatus(TicketStatus.NEW);
        testTicket.setPriority(TicketPriority.MEDIUM);
        testTicket.setTicketType(TicketType.INCIDENT);
        testTicket.setCreatedDate(LocalDateTime.now());
        testTicket.setUpdatedDate(LocalDateTime.now());
        testTicket = ticketRepository.save(testTicket);
    }

    @Test
    @DisplayName("1. In-app notification creation")
    void testInAppNotificationCreation() {
        Notification notification = notificationService.notifyUser(
                testUser1,
                testTicket,
                NotificationType.SUBMITTED,
                "Your test ticket was created."
        );

        assertNotNull(notification);
        assertNotNull(notification.getNotificationId());
        assertEquals("Your test ticket was created.", notification.getMessage());
        assertEquals(NotificationType.SUBMITTED, notification.getNotificationType());
        assertFalse(notification.getReadStatus());
        assertEquals(testUser1.getUserId(), notification.getUser().getUserId());
    }

    @Test
    @DisplayName("2. Unread notification count tracking")
    void testUnreadCountTracking() {
        assertEquals(0, notificationService.countUnread(testUser1.getUserId()));

        notificationService.notifyUser(testUser1, testTicket, NotificationType.SUBMITTED, "Msg 1");
        notificationService.notifyUser(testUser1, testTicket, NotificationType.UPDATED, "Msg 2");

        assertEquals(2, notificationService.countUnread(testUser1.getUserId()));
    }

    @Test
    @DisplayName("3. Mark own notification as read")
    void testMarkOwnNotificationRead() {
        Notification notification = notificationService.notifyUser(
                testUser1, testTicket, NotificationType.SUBMITTED, "Mark read test"
        );
        assertFalse(notification.getReadStatus());

        notificationService.markRead(notification.getNotificationId(), testUser1.getUserId());

        Notification updated = notificationRepository.findById(notification.getNotificationId()).orElseThrow();
        assertTrue(updated.getReadStatus());
        assertNotNull(updated.getReadDate());
        assertEquals(0, notificationService.countUnread(testUser1.getUserId()));
    }

    @Test
    @DisplayName("4. Cannot mark another user's notification as read (throws AccessDeniedException)")
    void testCannotMarkAnotherUserNotificationRead() {
        Notification notification = notificationService.notifyUser(
                testUser1, testTicket, NotificationType.SUBMITTED, "User 1 private alert"
        );

        assertThrows(AccessDeniedException.class, () -> {
            notificationService.markRead(notification.getNotificationId(), testUser2.getUserId());
        });

        // Verify still unread
        Notification notif = notificationRepository.findById(notification.getNotificationId()).orElseThrow();
        assertFalse(notif.getReadStatus());
    }

    @Test
    @DisplayName("5. Mark all notifications as read for user")
    void testMarkAllRead() {
        notificationService.notifyUser(testUser1, testTicket, NotificationType.SUBMITTED, "Msg A");
        notificationService.notifyUser(testUser1, testTicket, NotificationType.UPDATED, "Msg B");
        notificationService.notifyUser(testUser2, testTicket, NotificationType.UPDATED, "User 2 Msg");

        assertEquals(2, notificationService.countUnread(testUser1.getUserId()));
        assertEquals(1, notificationService.countUnread(testUser2.getUserId()));

        int marked = notificationService.markAllRead(testUser1.getUserId());
        assertEquals(2, marked);
        assertEquals(0, notificationService.countUnread(testUser1.getUserId()));
        // User 2's notification remains unread
        assertEquals(1, notificationService.countUnread(testUser2.getUserId()));
    }

    @Test
    @DisplayName("6. Preferences default values (inAppEnabled=true, emailEnabled=true)")
    void testPreferenceDefaultValues() {
        NotificationPreference pref = preferenceService.getOrCreatePreference(testUser1.getUserId());

        assertNotNull(pref);
        assertEquals(testUser1.getUserId(), pref.getUserId());
        assertTrue(pref.getInAppEnabled());
        assertTrue(pref.getEmailEnabled());
    }

    @Test
    @DisplayName("7. Update notification preferences and audit logging")
    void testUpdatePreferencesAndAuditLog() {
        NotificationPreference updated = preferenceService.updatePreference(
                testUser1.getUserId(),
                false,
                true,
                "127.0.0.1"
        );

        assertFalse(updated.getInAppEnabled());
        assertTrue(updated.getEmailEnabled());

        List<ActivityLog> logs = activityLogRepository.findAll().stream()
                .filter(l -> "NOTIFICATION_PREFERENCE_UPDATED".equals(l.getAction()) &&
                             l.getUser() != null &&
                             l.getUser().getUserId().equals(testUser1.getUserId()))
                .toList();

        assertFalse(logs.isEmpty());
        assertEquals("NOTIFICATION_PREFERENCE", logs.get(0).getEntityType());
        assertEquals(testUser1.getUserId(), logs.get(0).getEntityId());
    }

    @Test
    @DisplayName("8. Email-enabled user creates queue entry with status PENDING")
    void testEmailEnabledUserCreatesQueueEntry() {
        preferenceService.updatePreference(testUser1.getUserId(), true, true, "127.0.0.1");

        notificationService.notifyUser(testUser1, testTicket, NotificationType.SUBMITTED, "Email queue test");

        List<EmailNotificationQueue> queue = emailQueueRepository.findByUserUserIdOrderByCreatedAtDesc(testUser1.getUserId());
        assertFalse(queue.isEmpty());
        EmailNotificationQueue item = queue.get(0);
        assertEquals(EmailQueueStatus.PENDING, item.getStatus());
        assertEquals(testUser1.getEmail(), item.getRecipientEmail());
        assertTrue(item.getSubject().contains(testTicket.getReferenceNo()));
        assertEquals(0, item.getAttemptCount());
    }

    @Test
    @DisplayName("9. Email-disabled user does not create email queue entry")
    void testEmailDisabledUserDoesNotCreateQueueEntry() {
        preferenceService.updatePreference(testUser1.getUserId(), true, false, "127.0.0.1");

        notificationService.notifyUser(testUser1, testTicket, NotificationType.SUBMITTED, "No email test");

        List<EmailNotificationQueue> queue = emailQueueRepository.findByUserUserIdOrderByCreatedAtDesc(testUser1.getUserId());
        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("10. In-app disabled preference suppresses regular notifications but delivers SYSTEM alerts")
    void testInAppDisabledPreferenceBehavior() {
        preferenceService.updatePreference(testUser1.getUserId(), false, true, "127.0.0.1");

        // Regular ticket notification should NOT create in-app notification
        Notification regularNotif = notificationService.notifyUser(
                testUser1, testTicket, NotificationType.UPDATED, "Regular suppressed"
        );
        assertNull(regularNotif);

        // SYSTEM notification MUST still be delivered in-app!
        Notification systemNotif = notificationService.notifyUser(
                testUser1, testTicket, NotificationType.SYSTEM, "Security/System mandatory alert"
        );
        assertNotNull(systemNotif);
        assertEquals(NotificationType.SYSTEM, systemNotif.getNotificationType());
    }

    @Test
    @DisplayName("11. SMTP disabled does not crash application")
    void testSmtpDisabledDoesNotCrashApp() {
        DefaultEmailSenderService sender = new DefaultEmailSenderService(false, "helpdesk@university.edu", null);
        assertFalse(sender.isMailEnabled());
        assertThrows(IllegalStateException.class, () -> {
            sender.sendEmail("test@university.edu", "Subject", "Body");
        });
    }

    @Test
    @DisplayName("12. Failed email increments attempt count")
    void testFailedEmailIncrementsAttemptCount() {
        EmailNotificationQueue item = new EmailNotificationQueue();
        item.setUser(testUser1);
        item.setRecipientEmail(testUser1.getEmail());
        item.setSubject("Fail test");
        item.setMessage("Test message");
        item.setStatus(EmailQueueStatus.PENDING);
        item.setAttemptCount(0);
        item.setCreatedAt(LocalDateTime.now());
        item = emailQueueRepository.save(item);

        EmailSenderService failingSender = new EmailSenderService() {
            @Override
            public void sendEmail(String to, String subject, String body) throws Exception {
                throw new RuntimeException("SMTP connection refused");
            }
            @Override
            public boolean isMailEnabled() { return true; }
        };

        EmailQueueProcessor processor = new EmailQueueProcessor(
                emailQueueRepository, failingSender, activityLogRepository, true
        );

        processor.processItem(item);

        EmailNotificationQueue updated = emailQueueRepository.findById(item.getQueueId()).orElseThrow();
        assertEquals(1, updated.getAttemptCount());
        assertEquals(EmailQueueStatus.PENDING, updated.getStatus());
        assertNotNull(updated.getNextRetryAt());
        assertNotNull(updated.getLastError());
        assertTrue(updated.getLastError().contains("SMTP connection refused"));
    }

    @Test
    @DisplayName("13. Retry scheduling and maximum 3 attempts mark FAILED")
    void testRetrySchedulingAndMaxAttempts() {
        EmailNotificationQueue item = new EmailNotificationQueue();
        item.setUser(testUser1);
        item.setRecipientEmail(testUser1.getEmail());
        item.setSubject("Max retry test");
        item.setMessage("Test message");
        item.setStatus(EmailQueueStatus.PENDING);
        item.setAttemptCount(2); // Already failed twice
        item.setCreatedAt(LocalDateTime.now());
        final EmailNotificationQueue savedItem = emailQueueRepository.save(item);

        EmailSenderService failingSender = new EmailSenderService() {
            @Override
            public void sendEmail(String to, String subject, String body) throws Exception {
                throw new RuntimeException("Third attempt failed");
            }
            @Override
            public boolean isMailEnabled() { return true; }
        };

        EmailQueueProcessor processor = new EmailQueueProcessor(
                emailQueueRepository, failingSender, activityLogRepository, true
        );

        processor.processItem(savedItem);

        EmailNotificationQueue updated = emailQueueRepository.findById(savedItem.getQueueId()).orElseThrow();
        assertEquals(3, updated.getAttemptCount());
        assertEquals(EmailQueueStatus.FAILED, updated.getStatus());
        assertNull(updated.getNextRetryAt());

        // Verify audit log for failure
        List<ActivityLog> logs = activityLogRepository.findAll().stream()
                .filter(l -> "EMAIL_NOTIFICATION_FAILED".equals(l.getAction()) &&
                             l.getEntityId().equals(savedItem.getQueueId()))
                .toList();
        assertFalse(logs.isEmpty());
    }

    @Test
    @DisplayName("14. Sent email marks queue status SENT and logs audit activity")
    void testSentEmailMarksQueueSent() {
        EmailNotificationQueue item = new EmailNotificationQueue();
        item.setUser(testUser1);
        item.setRecipientEmail(testUser1.getEmail());
        item.setSubject("Success test");
        item.setMessage("Test message");
        item.setStatus(EmailQueueStatus.PENDING);
        item.setAttemptCount(0);
        item.setCreatedAt(LocalDateTime.now());
        final EmailNotificationQueue savedItem = emailQueueRepository.save(item);

        EmailSenderService successSender = new EmailSenderService() {
            @Override
            public void sendEmail(String to, String subject, String body) {
                // Successful delivery
            }
            @Override
            public boolean isMailEnabled() { return true; }
        };

        EmailQueueProcessor processor = new EmailQueueProcessor(
                emailQueueRepository, successSender, activityLogRepository, true
        );

        processor.processItem(savedItem);

        EmailNotificationQueue updated = emailQueueRepository.findById(savedItem.getQueueId()).orElseThrow();
        assertEquals(1, updated.getAttemptCount());
        assertEquals(EmailQueueStatus.SENT, updated.getStatus());
        assertNotNull(updated.getSentAt());
        assertNull(updated.getNextRetryAt());

        // Verify audit log
        List<ActivityLog> logs = activityLogRepository.findAll().stream()
                .filter(l -> "EMAIL_NOTIFICATION_SENT".equals(l.getAction()) &&
                             l.getEntityId().equals(savedItem.getQueueId()))
                .toList();
        assertFalse(logs.isEmpty());
    }

    @Test
    @DisplayName("15. Duplicate notification consolidation prevents spam")
    void testDuplicateNotificationConsolidation() {
        Notification first = notificationService.notifyUser(
                testUser1, testTicket, NotificationType.UPDATED, "Status changed"
        );
        assertNotNull(first);

        // Rapid identical update notification inside duplicate window
        Notification second = notificationService.notifyUser(
                testUser1, testTicket, NotificationType.UPDATED, "Status changed"
        );

        // Should return existing notification rather than creating duplicate
        assertEquals(first.getNotificationId(), second.getNotificationId());

        // However, critical events (ASSIGNED, ESCALATED, RESOLVED, CLOSED) MUST NOT be suppressed
        Notification assigned1 = notificationService.notifyUser(
                testUser1, testTicket, NotificationType.ASSIGNED, "Assigned to staff"
        );
        Notification assigned2 = notificationService.notifyUser(
                testUser1, testTicket, NotificationType.ASSIGNED, "Assigned to staff"
        );
        assertNotEquals(assigned1.getNotificationId(), assigned2.getNotificationId());
    }

    @Test
    @DisplayName("16. Ticket submitted event notification")
    void testTicketSubmittedEventNotification() {
        Notification studentNotif = notificationService.notifyUser(
                testUser1, testTicket, NotificationType.SUBMITTED,
                "Your ticket " + testTicket.getReferenceNo() + " was submitted successfully."
        );
        assertNotNull(studentNotif);
        assertEquals(NotificationType.SUBMITTED, studentNotif.getNotificationType());
        assertTrue(studentNotif.getMessage().contains(testTicket.getReferenceNo()));
    }

    @Test
    @DisplayName("17. Assigned and reassigned event notification")
    void testAssignedAndReassignedNotification() {
        Notification staffNotif = notificationService.notifyUser(
                testUser2, testTicket, NotificationType.ASSIGNED,
                "Ticket " + testTicket.getReferenceNo() + " was assigned to you."
        );
        assertNotNull(staffNotif);
        assertEquals(NotificationType.ASSIGNED, staffNotif.getNotificationType());
        assertEquals(testUser2.getUserId(), staffNotif.getUser().getUserId());

        // Reassigned notification
        Notification reassignNotif = notificationService.notifyUser(
                testUser2, testTicket, NotificationType.ASSIGNED,
                "Ticket " + testTicket.getReferenceNo() + " was reassigned to Alice Tester."
        );
        assertNotNull(reassignNotif);
    }

    @Test
    @DisplayName("18. Status update notification")
    void testStatusUpdateNotification() {
        Notification notif = notificationService.notifyUser(
                testUser1, testTicket, NotificationType.UPDATED,
                "Ticket " + testTicket.getReferenceNo() + " status changed to in progress."
        );
        assertNotNull(notif);
        assertEquals(NotificationType.UPDATED, notif.getNotificationType());
    }

    @Test
    @DisplayName("19. Resolved notification")
    void testResolvedNotification() {
        Notification notif = notificationService.notifyUser(
                testUser1, testTicket, NotificationType.RESOLVED,
                "Ticket " + testTicket.getReferenceNo() + " status changed to resolved."
        );
        assertNotNull(notif);
        assertEquals(NotificationType.RESOLVED, notif.getNotificationType());
    }

    @Test
    @DisplayName("20. Closed notification")
    void testClosedNotification() {
        Notification notif = notificationService.notifyUser(
                testUser1, testTicket, NotificationType.CLOSED,
                "Ticket " + testTicket.getReferenceNo() + " status changed to closed."
        );
        assertNotNull(notif);
        assertEquals(NotificationType.CLOSED, notif.getNotificationType());
    }
}
