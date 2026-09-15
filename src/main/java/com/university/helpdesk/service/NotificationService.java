package com.university.helpdesk.service;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public Notification notifyUser(
            UserAccount user,
            Ticket ticket,
            NotificationType type,
            String message
    ) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTicket(ticket);
        notification.setNotificationType(type);
        notification.setMessage(message);
        notification.setReadStatus(false);
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> getForUser(Long userId) {
        return notificationRepository.findByUserUserIdOrderByCreatedDateDesc(userId);
    }

    @Transactional
    public void markRead(Long notificationId, Long userId) {
        notificationRepository.findById(notificationId)
                .filter(n -> n.getUser().getUserId().equals(userId))
                .ifPresent(n -> {
                    n.setReadStatus(true);
                    n.setReadDate(LocalDateTime.now());
                    notificationRepository.save(n);
                });
    }
}
