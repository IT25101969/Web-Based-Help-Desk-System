package com.university.helpdesk.service;

import com.university.helpdesk.entity.ActivityLog;
import com.university.helpdesk.entity.NotificationPreference;
import com.university.helpdesk.entity.UserAccount;
import com.university.helpdesk.repository.ActivityLogRepository;
import com.university.helpdesk.repository.NotificationPreferenceRepository;
import com.university.helpdesk.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserAccountRepository userAccountRepository;
    private final ActivityLogRepository activityLogRepository;

    public NotificationPreferenceService(
            NotificationPreferenceRepository preferenceRepository,
            UserAccountRepository userAccountRepository,
            ActivityLogRepository activityLogRepository
    ) {
        this.preferenceRepository = preferenceRepository;
        this.userAccountRepository = userAccountRepository;
        this.activityLogRepository = activityLogRepository;
    }

    @Transactional
    public NotificationPreference getOrCreatePreference(Long userId) {
        return preferenceRepository.findById(userId)
                .orElseGet(() -> {
                    UserAccount user = userAccountRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("User was not found with ID: " + userId));
                    NotificationPreference defaultPref = new NotificationPreference(user);
                    return preferenceRepository.save(defaultPref);
                });
    }

    @Transactional
    public NotificationPreference updatePreference(
            Long userId,
            Boolean inAppEnabled,
            Boolean emailEnabled,
            String ipAddress
    ) {
        if (inAppEnabled == null || emailEnabled == null) {
            throw new IllegalArgumentException("Notification preferences must not be null.");
        }

        NotificationPreference preference = getOrCreatePreference(userId);
        preference.setInAppEnabled(inAppEnabled);
        preference.setEmailEnabled(emailEnabled);
        preference.setUpdatedAt(LocalDateTime.now());
        preference = preferenceRepository.save(preference);

        // Record audit activity
        ActivityLog log = new ActivityLog();
        log.setUser(preference.getUser());
        log.setAction("NOTIFICATION_PREFERENCE_UPDATED");
        log.setEntityType("NOTIFICATION_PREFERENCE");
        log.setEntityId(userId);
        log.setTimestamp(LocalDateTime.now());
        log.setIpAddress(ipAddress);
        activityLogRepository.save(log);

        return preference;
    }
}
