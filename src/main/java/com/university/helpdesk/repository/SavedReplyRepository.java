package com.university.helpdesk.repository;
import com.university.helpdesk.entity.SavedReply;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SavedReplyRepository extends JpaRepository<SavedReply,Long> {
    List<SavedReply> findAllByOrderByTitleAsc();
    List<SavedReply> findByEnabledTrueOrderByTitleAsc();
}
