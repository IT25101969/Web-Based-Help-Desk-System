package com.university.helpdesk.repository;

import com.university.helpdesk.entity.TicketStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketStatusHistoryRepository extends JpaRepository<TicketStatusHistory, Long> {
    List<TicketStatusHistory> findByTicketTicketIdOrderByChangedDateAsc(Long ticketId);
    void deleteByTicketTicketId(Long ticketId);
}
