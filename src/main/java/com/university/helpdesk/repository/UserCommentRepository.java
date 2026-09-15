package com.university.helpdesk.repository;

import com.university.helpdesk.entity.UserComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserCommentRepository extends JpaRepository<UserComment, Long> {
    List<UserComment> findByTicketTicketIdOrderByCreatedDateAsc(Long ticketId);
}
