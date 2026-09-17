package com.university.helpdesk.repository;

import com.university.helpdesk.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByTicketTicketIdOrderByUploadedDateAsc(Long ticketId);
    List<Attachment> findByTicketTicketId(Long ticketId);
    void deleteByTicketTicketId(Long ticketId);
}
