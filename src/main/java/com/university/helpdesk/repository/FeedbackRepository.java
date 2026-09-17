package com.university.helpdesk.repository;

import com.university.helpdesk.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    Optional<Feedback> findByTicketTicketId(Long ticketId);
    boolean existsByTicketTicketId(Long ticketId);
    void deleteByTicketTicketId(Long ticketId);
}
