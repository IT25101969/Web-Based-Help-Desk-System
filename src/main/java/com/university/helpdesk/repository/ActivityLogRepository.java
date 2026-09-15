package com.university.helpdesk.repository;

import com.university.helpdesk.entity.ActivityLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityLogRepository
        extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findTop15ByOrderByTimestampDesc();

    @Query("SELECT DISTINCT a.action FROM ActivityLog a ORDER BY a.action ASC")
    List<String> findDistinctActions();

    @Query("SELECT a FROM ActivityLog a LEFT JOIN FETCH a.user u " +
            "WHERE (:action IS NULL OR :action = '' OR a.action = :action) " +
            "AND (:search IS NULL OR :search = '' OR LOWER(u.universityId) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:start IS NULL OR a.timestamp >= :start) " +
            "AND (:endExclusive IS NULL OR a.timestamp < :endExclusive) " +
            "ORDER BY a.timestamp DESC")
    List<ActivityLog> findFilteredLogs(
            @Param("action") String action,
            @Param("search") String search,
            @Param("start") LocalDateTime start,
            @Param("endExclusive") LocalDateTime endExclusive,
            Pageable pageable
    );
}