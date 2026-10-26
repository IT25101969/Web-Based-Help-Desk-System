package com.university.helpdesk.repository;

import com.university.helpdesk.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityLogRepository
        extends JpaRepository<ActivityLog, Long> {

    boolean existsByUserUserId(Long userId);

    List<ActivityLog> findTop15ByOrderByTimestampDesc();

    @Query("SELECT DISTINCT a.action FROM ActivityLog a ORDER BY a.action ASC")
    List<String> findDistinctActions();

    @Query("SELECT DISTINCT a.entityType FROM ActivityLog a WHERE a.entityType IS NOT NULL AND a.entityType <> '' ORDER BY a.entityType ASC")
    List<String> findDistinctEntityTypes();

    long countByTimestampGreaterThanEqual(LocalDateTime timestamp);

    @Query("SELECT COUNT(a) FROM ActivityLog a WHERE LOWER(a.action) LIKE '%login%failed%' OR LOWER(a.action) LIKE '%locked%' OR LOWER(a.action) LIKE '%password%'")
    long countSecurityEvents();

    @Query(value = "SELECT a FROM ActivityLog a LEFT JOIN FETCH a.user u " +
            "WHERE (:action IS NULL OR :action = '' OR a.action = :action) " +
            "AND (:entityType IS NULL OR :entityType = '' OR a.entityType = :entityType) " +
            "AND (:search IS NULL OR :search = '' OR LOWER(u.universityId) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:start IS NULL OR a.timestamp >= :start) " +
            "AND (:endExclusive IS NULL OR a.timestamp < :endExclusive) " +
            "ORDER BY a.timestamp DESC, a.logId DESC",
            countQuery = "SELECT COUNT(a) FROM ActivityLog a LEFT JOIN a.user u " +
            "WHERE (:action IS NULL OR :action = '' OR a.action = :action) " +
            "AND (:entityType IS NULL OR :entityType = '' OR a.entityType = :entityType) " +
            "AND (:search IS NULL OR :search = '' OR LOWER(u.universityId) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:start IS NULL OR a.timestamp >= :start) " +
            "AND (:endExclusive IS NULL OR a.timestamp < :endExclusive)")
    Page<ActivityLog> findFilteredLogs(
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("search") String search,
            @Param("start") LocalDateTime start,
            @Param("endExclusive") LocalDateTime endExclusive,
            Pageable pageable
    );
}
