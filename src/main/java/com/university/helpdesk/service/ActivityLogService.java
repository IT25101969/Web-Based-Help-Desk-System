package com.university.helpdesk.service;

import com.university.helpdesk.entity.ActivityLog;
import com.university.helpdesk.entity.UserAccount;
import com.university.helpdesk.repository.ActivityLogRepository;
import org.springframework.stereotype.Service;

@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogService(
            ActivityLogRepository activityLogRepository
    ) {
        this.activityLogRepository = activityLogRepository;
    }

    public void log(
            UserAccount user,
            String action,
            String entityType,
            Long entityId,
            String ipAddress
    ) {

        ActivityLog log = new ActivityLog();

        log.setUser(user);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setIpAddress(ipAddress);

        activityLogRepository.save(log);
    }
}