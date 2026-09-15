package com.university.helpdesk.service;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.TicketAssignmentRepository;
import com.university.helpdesk.repository.TicketRepository;
import com.university.helpdesk.repository.UserAccountRepository;
import com.university.helpdesk.repository.UserCommentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService {

    private final UserCommentRepository commentRepository;
    private final UserAccountRepository userAccountRepository;
    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;
    private final TicketAssignmentRepository assignmentRepository;
    private final TicketService ticketService;

    public CommentService(
            UserCommentRepository commentRepository,
            UserAccountRepository userAccountRepository,
            TicketRepository ticketRepository,
            NotificationService notificationService,
            TicketAssignmentRepository assignmentRepository,
            TicketService ticketService
    ) {
        this.commentRepository = commentRepository;
        this.userAccountRepository = userAccountRepository;
        this.ticketRepository = ticketRepository;
        this.notificationService = notificationService;
        this.assignmentRepository = assignmentRepository;
        this.ticketService = ticketService;
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

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("User account is inactive.");
        }

        boolean isStudentOwner = ticket.getStudent().getUser().getUserId().equals(user.getUserId());

        if (isStudentOwner) {
            type = CommentType.PUBLIC;
        } else {
            ticketService.getAuthorizedSupportTicket(ticketId, universityId);
        }

        UserComment comment = new UserComment();
        comment.setTicket(ticket);
        comment.setUser(user);
        comment.setCommentText(text.trim());
        comment.setCommentType(type == null ? CommentType.PUBLIC : type);
        comment = commentRepository.save(comment);

        if (comment.getCommentType() == CommentType.PUBLIC) {
            if (!isStudentOwner) {
                notificationService.notifyUser(
                        ticket.getStudent().getUser(),
                        ticket,
                        NotificationType.UPDATED,
                        "A new reply was added to ticket " + ticket.getReferenceNo() + "."
                );
            } else {
                assignmentRepository.findFirstByTicketTicketIdAndStatusOrderByAssignedDateDesc(ticketId, AssignmentStatus.ACTIVE)
                        .ifPresent(active -> {
                            notificationService.notifyUser(
                                    active.getAssignedToUser(),
                                    ticket,
                                    NotificationType.UPDATED,
                                    "Student added a reply to ticket " + ticket.getReferenceNo() + "."
                            );
                        });
            }
        }

        return comment;
    }

    @Transactional(readOnly = true)
    public List<UserComment> getPublicComments(Long ticketId) {
        return commentRepository.findByTicketTicketIdOrderByCreatedDateAsc(ticketId)
                .stream()
                .filter(c -> c.getCommentType() == CommentType.PUBLIC)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserComment> getComments(Long ticketId) {
        return commentRepository.findByTicketTicketIdOrderByCreatedDateAsc(ticketId);
    }
}
