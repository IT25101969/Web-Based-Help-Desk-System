package com.university.helpdesk.controller;

import com.university.helpdesk.dto.ReportSummary;
import com.university.helpdesk.entity.Ticket;
import com.university.helpdesk.entity.UserAccount;
import com.university.helpdesk.repository.DepartmentRepository;
import com.university.helpdesk.repository.UserAccountRepository;
import com.university.helpdesk.service.ActivityLogService;
import com.university.helpdesk.service.ReportService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
public class ReportController {

    private final ReportService reportService;
    private final DepartmentRepository departmentRepository;
    private final UserAccountRepository userAccountRepository;
    private final ActivityLogService activityLogService;

    public ReportController(
            ReportService reportService,
            DepartmentRepository departmentRepository,
            UserAccountRepository userAccountRepository,
            ActivityLogService activityLogService
    ) {
        this.reportService = reportService;
        this.departmentRepository = departmentRepository;
        this.userAccountRepository = userAccountRepository;
        this.activityLogService = activityLogService;
    }

    @GetMapping({"/admin/reports", "/management/reports"})
    public String reports(
            @RequestParam(value = "start", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(value = "end", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            Authentication authentication,
            HttpServletRequest request,
            Model model
    ) {
        ReportSummary summary = reportService.buildSummary(start, end, departmentId);

        model.addAttribute("summary", summary);
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        model.addAttribute("selectedDepartmentId", departmentId);
        model.addAttribute(
                "basePath",
                request.getRequestURI().startsWith("/management/")
                        ? "/management"
                        : "/admin"
        );

        logReportAction(authentication, request, "REPORT_VIEWED");
        return "reports";
    }

    @GetMapping({
            "/admin/reports/export.csv",
            "/management/reports/export.csv"
    })
    public ResponseEntity<String> export(
            @RequestParam(value = "start", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(value = "end", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        List<Ticket> tickets =
                reportService.filteredTickets(start, end, departmentId);

        StringBuilder csv = new StringBuilder(
                "Reference,Subject,Category,Department,Priority,Status,Created\n"
        );

        for (Ticket ticket : tickets) {
            csv.append(csv(ticket.getReferenceNo())).append(',')
                    .append(csv(ticket.getSubject())).append(',')
                    .append(csv(ticket.getCategory().getCategoryName())).append(',')
                    .append(csv(ticket.getCategory().getDepartment() == null
                            ? "Manual Routing"
                            : ticket.getCategory().getDepartment().getDepartmentName()))
                    .append(',')
                    .append(ticket.getPriority()).append(',')
                    .append(ticket.getStatus()).append(',')
                    .append(ticket.getCreatedDate())
                    .append('\n');
        }

        logReportAction(authentication, request, "REPORT_EXPORTED");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=helpdesk-report.csv"
                )
                .body(csv.toString());
    }

    private void logReportAction(
            Authentication authentication,
            HttpServletRequest request,
            String action
    ) {
        UserAccount user = userAccountRepository
                .findByUniversityId(authentication.getName())
                .orElse(null);

        if (user != null) {
            activityLogService.log(
                    user,
                    action,
                    "REPORT",
                    null,
                    request.getRemoteAddr()
            );
        }
    }

    private String csv(String value) {
        if (value == null) return "";
        String escaped = value.replace(""", """");
        return """ + escaped + """;
    }
}
