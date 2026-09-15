package com.university.helpdesk.controller;

import com.university.helpdesk.dto.ReportSummary;
import com.university.helpdesk.entity.AccountStatus;
import com.university.helpdesk.entity.ActivityLog;
import com.university.helpdesk.entity.TicketStatus;
import com.university.helpdesk.repository.ActivityLogRepository;
import com.university.helpdesk.repository.TicketRepository;
import com.university.helpdesk.repository.UserAccountRepository;
import com.university.helpdesk.service.ReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class RoleDashboardController {

    private final TicketRepository ticketRepository;
    private final UserAccountRepository userAccountRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ReportService reportService;

    public RoleDashboardController(
            TicketRepository ticketRepository,
            UserAccountRepository userAccountRepository,
            ActivityLogRepository activityLogRepository,
            ReportService reportService
    ) {
        this.ticketRepository = ticketRepository;
        this.userAccountRepository = userAccountRepository;
        this.activityLogRepository = activityLogRepository;
        this.reportService = reportService;
    }

    @GetMapping("/staff/dashboard")
    public String staffDashboard() {
        return "staff-dashboard";
    }

    @GetMapping("/manager/dashboard")
    public String managerDashboard() {
        return "manager-dashboard";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        long totalTickets = ticketRepository.count();
        long unresolvedTickets = ticketRepository.countByStatusIn(List.of(
                TicketStatus.NEW,
                TicketStatus.ASSIGNED,
                TicketStatus.IN_PROGRESS,
                TicketStatus.ESCALATED
        ));
        long suspiciousUserCount = userAccountRepository.countByAccountStatusOrFailedLoginAttemptsGreaterThanEqual(
                AccountStatus.LOCKED,
                3
        );
        List<ActivityLog> recentActivities = activityLogRepository.findTop15ByOrderByTimestampDesc();

        model.addAttribute("totalTickets", totalTickets);
        model.addAttribute("unresolvedTickets", unresolvedTickets);
        model.addAttribute("suspiciousUserCount", suspiciousUserCount);
        model.addAttribute("recentActivities", recentActivities);

        return "admin-dashboard";
    }

    @GetMapping("/management/dashboard")
    public String managementDashboard(Model model) {
        ReportSummary summary = reportService.buildSummary(null, null, null);
        model.addAttribute("summary", summary);
        model.addAttribute("basePath", "/management");
        return "management-dashboard";
    }
}