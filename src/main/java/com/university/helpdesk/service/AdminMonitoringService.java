package com.university.helpdesk.service;

import com.university.helpdesk.dto.MonitoringSummary;
import com.university.helpdesk.entity.AccountStatus;
import com.university.helpdesk.entity.ActivityLog;
import com.university.helpdesk.entity.TicketStatus;
import com.university.helpdesk.repository.ActivityLogRepository;
import com.university.helpdesk.repository.TicketRepository;
import com.university.helpdesk.repository.UserAccountRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AdminMonitoringService {

    private final UserAccountRepository userAccountRepository;
    private final TicketRepository ticketRepository;
    private final ActivityLogRepository activityLogRepository;
    private final JdbcTemplate jdbcTemplate;

    public AdminMonitoringService(
            UserAccountRepository userAccountRepository,
            TicketRepository ticketRepository,
            ActivityLogRepository activityLogRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.userAccountRepository = userAccountRepository;
        this.ticketRepository = ticketRepository;
        this.activityLogRepository = activityLogRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public MonitoringSummary getMonitoringSummary() {
        long totalUsers = userAccountRepository.count();
        long activeUsers = userAccountRepository.countByAccountStatus(AccountStatus.ACTIVE);
        long lockedUsers = userAccountRepository.countByAccountStatus(AccountStatus.LOCKED);
        long disabledUsers = userAccountRepository.countByAccountStatus(AccountStatus.DISABLED);
        long suspiciousUsers = userAccountRepository.countByAccountStatusOrFailedLoginAttemptsGreaterThanEqual(
                AccountStatus.LOCKED,
                3
        );

        Map<TicketStatus, Long> statusCounts = new EnumMap<>(TicketStatus.class);
        for (TicketStatus status : TicketStatus.values()) {
            statusCounts.put(status, ticketRepository.countByStatus(status));
        }

        long unresolvedTickets = ticketRepository.countByStatusIn(List.of(
                TicketStatus.NEW,
                TicketStatus.ASSIGNED,
                TicketStatus.IN_PROGRESS,
                TicketStatus.ESCALATED
        ));

        long escalatedTickets = statusCounts.getOrDefault(TicketStatus.ESCALATED, 0L);

        Long pendingEmails = getEmailQueueCount("PENDING");
        Long failedEmails = getEmailQueueCount("FAILED");

        List<ActivityLog> recentLogs = activityLogRepository.findTop15ByOrderByTimestampDesc();

        return new MonitoringSummary(
                totalUsers,
                activeUsers,
                lockedUsers,
                disabledUsers,
                suspiciousUsers,
                statusCounts,
                unresolvedTickets,
                escalatedTickets,
                pendingEmails,
                failedEmails,
                recentLogs
        );
    }

    private Long getEmailQueueCount(String status) {
        try {
            List<String> tableNames = jdbcTemplate.query(
                    "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                            "WHERE TABLE_SCHEMA = DATABASE() " +
                            "AND UPPER(TABLE_NAME) = 'EMAIL_NOTIFICATION_QUEUE'",
                    (rs, rowNum) -> rs.getString("TABLE_NAME")
            );

            if (tableNames.isEmpty()) {
                return null;
            }

            String exactTableName = tableNames.get(0);
            if (exactTableName == null || !exactTableName.matches("^[a-zA-Z0-9_]+$")) {
                return null;
            }

            return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `" + exactTableName + "` WHERE UPPER(Status) = UPPER(?)",
                    Long.class,
                    status
            );
        } catch (Exception ex) {
            return null;
        }
    }
}
