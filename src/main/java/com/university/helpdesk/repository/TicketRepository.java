package com.university.helpdesk.repository;

import com.university.helpdesk.entity.Ticket;
import com.university.helpdesk.entity.TicketPriority;
import com.university.helpdesk.entity.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByStudentUserIdOrderByCreatedDateDesc(Long userId);
    List<Ticket> findAllByOrderByCreatedDateDesc();
    long countByStatus(TicketStatus status);
    List<Ticket> findByCategoryDepartmentDepartmentIdInOrderByCreatedDateDesc(Collection<Long> departmentIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.ticketId = :ticketId")
    Optional<Ticket> findByIdWithLock(@Param("ticketId") Long ticketId);

    @Query("SELECT t FROM Ticket t WHERE t.category.department.departmentId IN :departmentIds " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:priority IS NULL OR t.priority = :priority) " +
            "ORDER BY t.createdDate DESC")
    List<Ticket> findByDepartmentIdsWithFilters(
            @Param("departmentIds") Collection<Long> departmentIds,
            @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority
    );

    @Query("SELECT t FROM Ticket t WHERE " +
            "((:unmappedOnly = true AND t.category.department IS NULL) OR " +
            " (:unmappedOnly = false AND (:departmentId IS NULL OR t.category.department.departmentId = :departmentId))) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:priority IS NULL OR t.priority = :priority) " +
            "ORDER BY t.createdDate DESC")
    List<Ticket> findAdminTicketsWithFilters(
            @Param("departmentId") Long departmentId,
            @Param("unmappedOnly") boolean unmappedOnly,
            @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority
    );
}
