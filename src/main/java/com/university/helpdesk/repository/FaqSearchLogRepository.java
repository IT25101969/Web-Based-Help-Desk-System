package com.university.helpdesk.repository;

import com.university.helpdesk.entity.FaqSearchLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FaqSearchLogRepository extends JpaRepository<FaqSearchLog, Long> {

    long countByResultsCount(int resultsCount);

    @Query("SELECT MAX(l.query), l.normalizedQuery, COUNT(l), " +
           "SUM(CASE WHEN l.resultsCount = 0 THEN 1L ELSE 0L END), " +
           "AVG(l.resultsCount * 1.0) " +
           "FROM FaqSearchLog l " +
           "WHERE l.normalizedQuery IS NOT NULL AND TRIM(l.normalizedQuery) <> '' " +
           "GROUP BY l.normalizedQuery " +
           "ORDER BY COUNT(l) DESC")
    List<Object[]> findTopSearchTermsRaw(Pageable pageable);

    @Query("SELECT MAX(l.query), l.normalizedQuery, COUNT(l) " +
           "FROM FaqSearchLog l " +
           "WHERE l.resultsCount = 0 AND l.normalizedQuery IS NOT NULL AND TRIM(l.normalizedQuery) <> '' " +
           "GROUP BY l.normalizedQuery " +
           "ORDER BY COUNT(l) DESC")
    List<Object[]> findContentGapsRaw(Pageable pageable);

    @Query("SELECT c.categoryName, COUNT(l) " +
           "FROM FaqSearchLog l JOIN l.category c " +
           "GROUP BY c.categoryName " +
           "ORDER BY COUNT(l) DESC")
    List<Object[]> findCategoryDistributionRaw(Pageable pageable);

    @Query("SELECT l FROM FaqSearchLog l " +
           "LEFT JOIN FETCH l.user " +
           "LEFT JOIN FETCH l.category " +
           "ORDER BY l.searchedAt DESC")
    List<FaqSearchLog> findRecentSearches(Pageable pageable);
}
