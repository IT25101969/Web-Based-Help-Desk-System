package com.university.helpdesk.repository;

import com.university.helpdesk.entity.Faq;
import com.university.helpdesk.entity.FaqStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {
    List<Faq> findByStatusOrderByQuestionAsc(FaqStatus status);
    List<Faq> findByStatusAndQuestionContainingIgnoreCaseOrderByQuestionAsc(FaqStatus status, String keyword);
}
