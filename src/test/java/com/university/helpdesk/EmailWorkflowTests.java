package com.university.helpdesk;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class EmailWorkflowTests extends TestAccounts {
    @Autowired TicketService tickets;
    @Autowired CategoryRepository categories;
    @Autowired UserAccountRepository users;
    @Autowired EmailNotificationQueueRepository emails;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationPreferenceService preferences;
    @Autowired CommentService comments;
    @Autowired PasswordResetService resets;
    @Autowired OverdueAlertService overdue;

    private Ticket ticket() {
        var category = new Category();
        category.setCategoryName("Email test " + UUID.randomUUID());
        categories.save(category);
        return tickets.createTicket("STU001", category.getCategoryId(), TicketType.INCIDENT,
                "Email workflow", "Test request", "Connectivity", "MEDIUM");
    }

    @Test void submissionPublicReplyAndStatusQueueEmailButInternalReplyDoesNot() {
        Ticket ticket = ticket();
        var queued = emails.findByTicketTicketIdOrderByCreatedAtDesc(ticket.getTicketId());
        assertTrue(queued.stream().anyMatch(e -> e.getSubject().contains("Ticket Submitted")));
        int before = queued.size();
        comments.addComment(ticket.getTicketId(), "ADM001", "Staff-only details", CommentType.INTERNAL);
        assertEquals(before, emails.findByTicketTicketIdOrderByCreatedAtDesc(ticket.getTicketId()).size());
        comments.addComment(ticket.getTicketId(), "ADM001", "Public response", CommentType.PUBLIC);
        assertEquals(before + 1, emails.findByTicketTicketIdOrderByCreatedAtDesc(ticket.getTicketId()).size());
        tickets.changeStatus(ticket.getTicketId(), TicketStatus.IN_PROGRESS, "ADM001", "Working on it");
        assertTrue(emails.findByTicketTicketIdOrderByCreatedAtDesc(ticket.getTicketId()).size() > before + 1);
        assertFalse(notifications.findByUserUserIdOrderByCreatedDateDesc(ticket.getStudent().getUser().getUserId()).isEmpty());
    }

    @Test void overdueEmailOnlyPreferenceQueuesOnceAcrossSchedulerRuns() {
        Ticket ticket = ticket();
        ticket.setCreatedDate(LocalDateTime.now().minusDays(10));
        var admin = users.findByUniversityId("ADM001").orElseThrow();
        preferences.updatePreference(admin.getUserId(), false, true, "127.0.0.1");
        assertTrue(overdue.alert(ticket.getTicketId(), LocalDateTime.now()) > 0);
        assertEquals(0, overdue.alert(ticket.getTicketId(), LocalDateTime.now()));
        assertEquals(1, emails.findByTicketTicketIdOrderByCreatedAtDesc(ticket.getTicketId()).stream()
                .filter(e -> e.getUser().getUserId().equals(admin.getUserId()) && e.getSubject().contains("Overdue")).count());
        assertTrue(notifications.findByUserUserIdOrderByCreatedDateDesc(admin.getUserId()).stream()
                .noneMatch(n -> n.getNotificationType() == NotificationType.OVERDUE));
    }

    @Test void resetEmailContainsUsableLinkEvenWhenNotificationPreferenceIsOff() {
        var student = users.findByUniversityId("STU001").orElseThrow();
        preferences.updatePreference(student.getUserId(), true, false, "127.0.0.1");
        String token = resets.createResetToken("STU001", "127.0.0.1").orElseThrow();
        var queued = emails.findByUserUserIdOrderByCreatedAtDesc(student.getUserId());
        assertTrue(queued.stream().anyMatch(e -> e.getMessage().contains("/reset-password?token=" + token)));
        assertTrue(resets.isValidToken(token));
    }
}
