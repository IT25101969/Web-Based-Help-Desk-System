package com.university.helpdesk.repository;

import com.university.helpdesk.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogRepository
        extends JpaRepository<ActivityLog, Long> {
}