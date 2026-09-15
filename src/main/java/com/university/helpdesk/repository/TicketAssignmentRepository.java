package com.university.helpdesk.repository;

import com.university.helpdesk.entity.AssignmentStatus;
import com.university.helpdesk.entity.Ticket;
import com.university.helpdesk.entity.TicketAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketAssignmentRepository extends JpaRepository<TicketAssignment, Long> {
    Optional<TicketAssignment> findFirstByTicketTicketIdAndStatusOrderByAssignedDateDesc(Long ticketId, AssignmentStatus status);
    List<TicketAssignment> findByTicketTicketIdOrderByAssignedDateDesc(Long ticketId);
    List<TicketAssignment> findByTicketTicketIdAndStatus(Long ticketId, AssignmentStatus status);
    List<TicketAssignment> findByTicketInAndStatus(Collection<Ticket> tickets, AssignmentStatus status);
    List<TicketAssignment> findByStatus(AssignmentStatus status);
    long countByAssignedToUserUserIdAndStatus(Long userId, AssignmentStatus status);
}
