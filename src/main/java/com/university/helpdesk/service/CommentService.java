package com.university.helpdesk.service;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.TicketRepository;
import com.university.helpdesk.repository.UserAccountRepository;
import com.university.helpdesk.repository.UserCommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService {

    private final UserCommentRepository commentRepository;
    private final UserAccountRepository userAccountRepository;
    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;

    public CommentService(
            UserCommentRepository commentRepository,
            UserAccountRepository userAccountRepository,
            TicketRepository ticketRepository,
            NotificationService notificationService
    ) {
        this.commentRepository = commentRepository;
        this.userAccountRepository = userAccountRepository;
        this.ticketRepository = ticketRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public UserComment addComment(
            Long ticketId,
            String universityId,
            String text,
            CommentType type
    ) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Comment cannot be empty.");
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket was not found."));

        UserAccount user = userAccountRepository.findByUniversityId(universityId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));

        UserComment comment = new UserComment();
        comment.setTicket(ticket);
        comment.setUser(user);
        comment.setCommentText(text.trim());
        comment.setCommentType(type == null ? CommentType.PUBLIC : type);
        comment = commentRepository.save(comment);

        if (comment.getCommentType() == CommentType.PUBLIC &&
                !ticket.getStudent().getUser().getUserId().equals(user.getUserId())) {
            notificationService.notifyUser(
                    ticket.getStudent().getUser(),
                    ticket,
                    NotificationType.UPDATED,
                    "A new reply was added to ticket " + ticket.getReferenceNo() + "."
            );
        }

        return comment;
    }

    @Transactional(readOnly = true)
    public List<UserComment> getComments(Long ticketId) {
        return commentRepository.findByTicketTicketIdOrderByCreatedDateAsc(ticketId);
    }
}
