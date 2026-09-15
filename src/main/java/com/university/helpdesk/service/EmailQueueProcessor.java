package com.university.helpdesk.service;

import com.university.helpdesk.entity.ActivityLog;
import com.university.helpdesk.entity.EmailNotificationQueue;
import com.university.helpdesk.entity.EmailQueueStatus;
import com.university.helpdesk.repository.ActivityLogRepository;
import com.university.helpdesk.repository.EmailNotificationQueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EmailQueueProcessor {

    private static final Logger log = LoggerFactory.getLogger(EmailQueueProcessor.class);
    private static final int MAX_ATTEMPTS = 3;

    private final EmailNotificationQueueRepository queueRepository;
    private final EmailSenderService emailSenderService;
    private final ActivityLogRepository activityLogRepository;
    private final boolean schedulerEnabled;

    public EmailQueueProcessor(
            EmailNotificationQueueRepository queueRepository,
            EmailSenderService emailSenderService,
            ActivityLogRepository activityLogRepository,
            @Value("${app.mail.scheduler.enabled:true}") boolean schedulerEnabled
    ) {
        this.queueRepository = queueRepository;
        this.emailSenderService = emailSenderService;
        this.activityLogRepository = activityLogRepository;
        this.schedulerEnabled = schedulerEnabled;
    }

    @Scheduled(fixedDelayString = "${app.mail.scheduler.delay:10000}")
    public void scheduledProcess() {
        if (!schedulerEnabled) {
            return;
        }
        if (!emailSenderService.isMailEnabled()) {
            return;
        }
        processPendingEmails();
    }

    public int processPendingEmails() {
        if (!emailSenderService.isMailEnabled()) {
            log.debug("Skipping email queue processing because mail is disabled (app.mail.enabled=false).");
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        List<EmailNotificationQueue> pendingList = queueRepository
                .findPendingEmailsEligible(EmailQueueStatus.PENDING, now);

        if (pendingList.isEmpty()) {
            return 0;
        }

        int processedCount = 0;
        for (EmailNotificationQueue item : pendingList) {
            processItem(item);
            processedCount++;
        }
        return processedCount;
    }

    @Transactional
    public void processItem(EmailNotificationQueue item) {
        // Double check status to avoid race conditions
        if (item.getStatus() != EmailQueueStatus.PENDING) {
            return;
        }

        item.setAttemptCount(item.getAttemptCount() + 1);
        LocalDateTime now = LocalDateTime.now();

        try {
            emailSenderService.sendEmail(
                    item.getRecipientEmail(),
                    item.getSubject(),
                    item.getMessage()
            );

            item.setStatus(EmailQueueStatus.SENT);
            item.setSentAt(now);
            item.setNextRetryAt(null);
            item.setLastError(null);
            item.setUpdatedAt(now);
            queueRepository.save(item);

            // Audit log
            ActivityLog activityLog = new ActivityLog();
            activityLog.setUser(item.getUser());
            activityLog.setAction("EMAIL_NOTIFICATION_SENT");
            activityLog.setEntityType("EMAIL_NOTIFICATION_QUEUE");
            activityLog.setEntityId(item.getQueueId());
            activityLog.setTimestamp(now);
            activityLogRepository.save(activityLog);

        } catch (Exception ex) {
            String error = ex.getMessage() != null
                    ? (ex.getMessage().length() > 950 ? ex.getMessage().substring(0, 950) : ex.getMessage())
                    : ex.getClass().getSimpleName();
            item.setLastError(error);
            item.setUpdatedAt(now);

            if (item.getAttemptCount() >= MAX_ATTEMPTS) {
                item.setStatus(EmailQueueStatus.FAILED);
                item.setNextRetryAt(null);
                log.warn("Email delivery permanently FAILED for queue item ID {}: {}", item.getQueueId(), error);

                // Audit log
                ActivityLog activityLog = new ActivityLog();
                activityLog.setUser(item.getUser());
                activityLog.setAction("EMAIL_NOTIFICATION_FAILED");
                activityLog.setEntityType("EMAIL_NOTIFICATION_QUEUE");
                activityLog.setEntityId(item.getQueueId());
                activityLog.setTimestamp(now);
                activityLogRepository.save(activityLog);
            } else {
                // Exponential backoff
                long delayMinutes = item.getAttemptCount() == 1 ? 1 : 5;
                item.setNextRetryAt(now.plusMinutes(delayMinutes));
                log.info("Email delivery attempt {} failed for queue item ID {}. Scheduled next retry at {}",
                        item.getAttemptCount(), item.getQueueId(), item.getNextRetryAt());
            }

            queueRepository.save(item);
        }
    }
}
