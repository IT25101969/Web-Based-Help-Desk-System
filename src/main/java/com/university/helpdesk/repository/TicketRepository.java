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
    long countByStatusIn(Collection<TicketStatus> statuses);
    List<Ticket> findByCategoryDepartmentDepartmentIdInOrderByCreatedDateDesc(Collection<Long> departmentIds);

    @org.springframework.data.jpa.repository.Query("SELECT t FROM Ticket t LEFT JOIN FETCH t.category c LEFT JOIN FETCH c.department d " +
            "WHERE (:start IS NULL OR t.createdDate >= :start) " +
            "AND (:endExclusive IS NULL OR t.createdDate < :endExclusive) " +
            "AND (:departmentId IS NULL OR d.departmentId = :departmentId) " +
            "ORDER BY t.createdDate DESC")
    List<Ticket> findFilteredTickets(
            @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
            @org.springframework.data.repository.query.Param("endExclusive") java.time.LocalDateTime endExclusive,
            @org.springframework.data.repository.query.Param("departmentId") Long departmentId
    );
}

