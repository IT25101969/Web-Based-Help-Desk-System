package com.university.helpdesk.service;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.FeedbackRepository;
import com.university.helpdesk.repository.TicketRepository;
import com.university.helpdesk.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final TicketRepository ticketRepository;
    private final UserAccountRepository userAccountRepository;

    public FeedbackService(
            FeedbackRepository feedbackRepository,
            TicketRepository ticketRepository,
            UserAccountRepository userAccountRepository
    ) {
        this.feedbackRepository = feedbackRepository;
        this.ticketRepository = ticketRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public Feedback submit(
            Long ticketId,
            String universityId,
            int rating,
            String comment
    ) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5.");
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket was not found."));

        UserAccount student = userAccountRepository.findByUniversityId(universityId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));

        if (!ticket.getStudent().getUserId().equals(student.getUserId())) {
            throw new SecurityException("You cannot submit feedback for this ticket.");
        }

        if (ticket.getStatus() != TicketStatus.RESOLVED &&
                ticket.getStatus() != TicketStatus.CLOSED) {
            throw new IllegalStateException("Feedback is available only after the ticket is resolved or closed.");
        }

        if (feedbackRepository.existsByTicketTicketId(ticketId)) {
            throw new IllegalStateException("Feedback has already been submitted for this ticket.");
        }

        Feedback feedback = new Feedback();
        feedback.setTicket(ticket);
        feedback.setStudent(student);
        feedback.setRating(rating);
        feedback.setComment(comment == null || comment.isBlank() ? null : comment.trim());
        return feedbackRepository.save(feedback);
    }

    @Transactional(readOnly = true)
    public Optional<Feedback> findForTicket(Long ticketId) {
        return feedbackRepository.findByTicketTicketId(ticketId);
    }
}
