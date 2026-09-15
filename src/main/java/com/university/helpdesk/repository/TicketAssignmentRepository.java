package com.university.helpdesk.repository;

import com.university.helpdesk.entity.AssignmentStatus;
import com.university.helpdesk.entity.TicketAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketAssignmentRepository extends JpaRepository<TicketAssignment, Long> {
    Optional<TicketAssignment> findFirstByTicketTicketIdAndStatusOrderByAssignedDateDesc(Long ticketId, AssignmentStatus status);
    List<TicketAssignment> findByTicketTicketIdOrderByAssignedDateDesc(Long ticketId);
    long countByAssignedToUserUserIdAndStatus(Long userId, AssignmentStatus status);
}
