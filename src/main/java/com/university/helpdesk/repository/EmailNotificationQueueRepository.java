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
}
