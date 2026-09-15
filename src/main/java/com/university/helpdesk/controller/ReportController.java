package com.university.helpdesk.controller;

import com.university.helpdesk.dto.ReportSummary;
import com.university.helpdesk.entity.AssignmentStatus;
import com.university.helpdesk.entity.Ticket;
import com.university.helpdesk.entity.TicketAssignment;
import com.university.helpdesk.entity.UserAccount;
import com.university.helpdesk.repository.DepartmentRepository;
import com.university.helpdesk.repository.TicketAssignmentRepository;
import com.university.helpdesk.repository.UserAccountRepository;
import com.university.helpdesk.service.ActivityLogService;
import com.university.helpdesk.service.ReportService;

import org.springframework.security.access.AccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class ReportController {

    private final ReportService reportService;
    private final DepartmentRepository departmentRepository;
    private final UserAccountRepository userAccountRepository;
    private final TicketAssignmentRepository assignmentRepository;
    private final ActivityLogService activityLogService;

    public ReportController(
            ReportService reportService,
            DepartmentRepository departmentRepository,
            UserAccountRepository userAccountRepository,
            TicketAssignmentRepository assignmentRepository,
            ActivityLogService activityLogService
    ) {
        this.reportService = reportService;
        this.departmentRepository = departmentRepository;
        this.userAccountRepository = userAccountRepository;
        this.assignmentRepository = assignmentRepository;
        this.activityLogService = activityLogService;
    }

    @GetMapping({"/admin/reports", "/management/reports", "/reports"})
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
        String uri = request.getRequestURI();
        if ("/reports".equals(uri)) {
            boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_System Administrator".equals(a.getAuthority()));
            boolean isMgmt = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_University Management".equals(a.getAuthority()));

            String qs = request.getQueryString() != null ? "?" + request.getQueryString() : "";
            if (isAdmin) {
                return "redirect:/admin/reports" + qs;
            } else if (isMgmt) {
                return "redirect:/management/reports" + qs;
            } else {
                throw new AccessDeniedException("You do not have permission to access reports.");
            }
        }

        Optional<String> validationError = reportService.validateFilters(start, end, departmentId);
        ReportSummary summary;
        if (validationError.isPresent()) {
            model.addAttribute("error", validationError.get());
            summary = reportService.emptySummary();
        } else {
            summary = reportService.buildSummary(start, end, departmentId);
        }

        model.addAttribute("summary", summary);
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        model.addAttribute("selectedDepartmentId", departmentId);
        model.addAttribute(
                "basePath",
                uri.startsWith("/management")
                        ? "/management"
                        : "/admin"
        );

        logReportAction(authentication, request, "REPORT_VIEWED");
        return "reports";
    }

    @GetMapping({
            "/admin/reports/export",
            "/admin/reports/export.csv",
            "/management/reports/export",
            "/management/reports/export.csv",
            "/reports/export",
            "/reports/export.csv"
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
        String uri = request.getRequestURI();
        if (uri.startsWith("/reports/export")) {
            boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_System Administrator".equals(a.getAuthority()));
            boolean isMgmt = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_University Management".equals(a.getAuthority()));
            if (!isAdmin && !isMgmt) {
                throw new AccessDeniedException("You do not have permission to export reports.");
            }
        }
        Optional<String> validationError = reportService.validateFilters(start, end, departmentId);
        if (validationError.isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.parseMediaType("text/plain; charset=UTF-8"))
                    .body(validationError.get());
        }

        List<Ticket> tickets =
                reportService.filteredTickets(start, end, departmentId);

        Set<Long> ticketIds = tickets.stream()
                .map(Ticket::getTicketId)
                .collect(Collectors.toSet());

        Map<Long, String> assignedStaff = new HashMap<>();
        if (!ticketIds.isEmpty()) {
            List<TicketAssignment> assignments =
                    assignmentRepository.findActiveAssignmentsForTicketIds(AssignmentStatus.ACTIVE, ticketIds);
            for (TicketAssignment a : assignments) {
                if (a.getAssignedToUser() != null && a.getTicket() != null) {
                    UserAccount staff = a.getAssignedToUser();
                    assignedStaff.put(
                            a.getTicket().getTicketId(),
                            staff.getFirstName() + " " + staff.getLastName() + " (" + staff.getUniversityId() + ")"
                    );
                }
            }
        }

        StringBuilder csv = new StringBuilder(
                "Reference,Subject,Category,Department,Priority,Status,Created Date,Resolved Date,Assigned Staff\n"
        );

        for (Ticket ticket : tickets) {
            String departmentName = (ticket.getCategory() == null || ticket.getCategory().getDepartment() == null)
                    ? "Manual Routing"
                    : ticket.getCategory().getDepartment().getDepartmentName();

            String staffName = assignedStaff.getOrDefault(ticket.getTicketId(), "Unassigned");

            csv.append(csv(ticket.getReferenceNo())).append(',')
                    .append(csv(ticket.getSubject())).append(',')
                    .append(csv(ticket.getCategory() != null ? ticket.getCategory().getCategoryName() : "")).append(',')
                    .append(csv(departmentName)).append(',')
                    .append(ticket.getPriority()).append(',')
                    .append(ticket.getStatus()).append(',')
                    .append(ticket.getCreatedDate() != null ? ticket.getCreatedDate().toString() : "").append(',')
                    .append(ticket.getResolvedDate() != null ? ticket.getResolvedDate().toString() : "").append(',')
                    .append(csv(staffName))
                    .append('\n');
        }

        logReportAction(authentication, request, "REPORT_EXPORTED");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"helpdesk-report.csv\""
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
        String sanitized = value;
        int firstNonBlank = -1;
        for (int i = 0; i < sanitized.length(); i++) {
            char c = sanitized.charAt(i);
            if (c != ' ' && c != '\t') {
                firstNonBlank = i;
                break;
            }
        }
        if (firstNonBlank != -1) {
            char c = sanitized.charAt(firstNonBlank);
            if (c == '=' || c == '+' || c == '-' || c == '@') {
                sanitized = "'" + sanitized;
            }
        }
        String escaped = sanitized.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}

