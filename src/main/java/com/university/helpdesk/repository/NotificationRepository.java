package com.university.helpdesk.repository;

import com.university.helpdesk.entity.Notification;
import com.university.helpdesk.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserUserIdOrderByCreatedDateDesc(Long userId);

    List<Notification> findByUserUserIdAndReadStatusFalseOrderByCreatedDateDesc(Long userId);

    long countByUserUserIdAndReadStatusFalse(Long userId);

    Optional<Notification> findFirstByUserUserIdAndTicketTicketIdAndNotificationTypeAndCreatedDateAfterOrderByCreatedDateDesc(
            Long userId,
            Long ticketId,
            NotificationType type,
            LocalDateTime after
    );

    @Modifying
    @Query("UPDATE Notification n SET n.readStatus = true, n.readDate = :now WHERE n.user.userId = :userId AND n.readStatus = false")
    int markAllReadForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
