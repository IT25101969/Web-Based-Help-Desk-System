package com.university.helpdesk.repository;

import com.university.helpdesk.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserUserIdOrderByCreatedDateDesc(Long userId);
    long countByUserUserIdAndReadStatusFalse(Long userId);
}
