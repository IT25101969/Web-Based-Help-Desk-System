package com.university.helpdesk.controller;

import com.university.helpdesk.entity.ActivityLog;
import com.university.helpdesk.repository.ActivityLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/admin/activity-logs")
public class AdminActivityLogController {

    private final ActivityLogRepository activityLogRepository;

    public AdminActivityLogController(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    @GetMapping
    public String listLogs(
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "start", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(value = "end", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            Model model
    ) {
        if (start != null && end != null && start.isAfter(end)) {
            model.addAttribute("error", "Start date cannot be after end date.");
            model.addAttribute("logs", Collections.emptyList());
        } else {
            LocalDateTime startDateTime = start != null ? start.atStartOfDay() : null;
            LocalDateTime endExclusive = end != null ? end.plusDays(1).atStartOfDay() : null;

            String trimmedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
            String trimmedAction = (action != null && !action.trim().isEmpty()) ? action.trim() : null;

            int pageLimit = Math.min(Math.max(limit, 10), 500);

            List<ActivityLog> logs = activityLogRepository.findFilteredLogs(
                    trimmedAction,
                    trimmedSearch,
                    startDateTime,
                    endExclusive,
                    PageRequest.of(0, pageLimit)
            );
            model.addAttribute("logs", logs);
        }

        model.addAttribute("actions", activityLogRepository.findDistinctActions());
        model.addAttribute("selectedAction", action);
        model.addAttribute("search", search);
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        return "admin-activity-logs";
    }
}
