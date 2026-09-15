package com.university.helpdesk.repository;

import com.university.helpdesk.entity.Faq;
import com.university.helpdesk.entity.FaqStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {
    List<Faq> findByStatusOrderByQuestionAsc(FaqStatus status);
    List<Faq> findByStatusAndCategoryCategoryIdOrderByQuestionAsc(FaqStatus status, Long categoryId);
    List<Faq> findByStatusAndQuestionContainingIgnoreCaseOrderByQuestionAsc(FaqStatus status, String keyword);
    boolean existsByQuestion(String question);

    @Query("SELECT f FROM Faq f LEFT JOIN f.category c WHERE f.status = :status " +
           "AND (:categoryId IS NULL OR c.categoryId = :categoryId) " +
           "AND (LOWER(f.question) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "  OR LOWER(CAST(f.answer AS string)) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "  OR (c.categoryName IS NOT NULL AND LOWER(c.categoryName) LIKE LOWER(CONCAT('%', :q, '%')))) " +
           "ORDER BY f.question ASC")
    List<Faq> searchPublished(
            @Param("status") FaqStatus status,
            @Param("q") String q,
            @Param("categoryId") Long categoryId
    );
}
