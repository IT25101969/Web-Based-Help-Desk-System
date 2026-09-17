package com.university.helpdesk.repository;

import com.university.helpdesk.entity.EmailNotificationQueue;
import com.university.helpdesk.entity.EmailQueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailNotificationQueueRepository extends JpaRepository<EmailNotificationQueue, Long> {

    @Query("SELECT q FROM EmailNotificationQueue q " +
           "WHERE q.status = :status " +
           "AND (q.nextRetryAt IS NULL OR q.nextRetryAt <= :now) " +
           "ORDER BY q.createdAt ASC")
    List<EmailNotificationQueue> findPendingEmailsEligible(
            @Param("status") EmailQueueStatus status,
            @Param("now") LocalDateTime now
    );

    long countByStatus(EmailQueueStatus status);

    List<EmailNotificationQueue> findByUserUserIdOrderByCreatedAtDesc(Long userId);

    List<EmailNotificationQueue> findByTicketTicketIdOrderByCreatedAtDesc(Long ticketId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE EmailNotificationQueue q SET q.notification = null WHERE q.notification.notificationId = :notificationId")
    void disassociateNotification(@Param("notificationId") Long notificationId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE EmailNotificationQueue q SET q.notification = null WHERE q.user.userId = :userId")
    void disassociateAllNotificationsForUser(@Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE EmailNotificationQueue q SET q.notification = null WHERE q.notification.ticket.ticketId = :ticketId OR q.ticket.ticketId = :ticketId")
    void disassociateNotificationByTicketId(@Param("ticketId") Long ticketId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM EmailNotificationQueue q WHERE q.ticket.ticketId = :ticketId")
    void deleteByTicketTicketId(@Param("ticketId") Long ticketId);
}
