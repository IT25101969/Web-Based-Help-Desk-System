package com.university.helpdesk.repository;

import com.university.helpdesk.entity.Ticket;
import com.university.helpdesk.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByStudentUserIdOrderByCreatedDateDesc(Long userId);
    List<Ticket> findAllByOrderByCreatedDateDesc();
    long countByStatus(TicketStatus status);
    List<Ticket> findByCategoryDepartmentDepartmentIdInOrderByCreatedDateDesc(Collection<Long> departmentIds);
    boolean existsByReferenceNo(String referenceNo);
    long countByStudentUserId(Long userId);
    long countByStudentUserIdAndStatusIn(Long userId, Collection<TicketStatus> statuses);
    long countByStudentUserIdAndStatus(Long userId, TicketStatus status);
}
