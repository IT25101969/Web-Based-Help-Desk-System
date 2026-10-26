package com.university.helpdesk.controller;

import com.university.helpdesk.entity.ActivityLog;
import com.university.helpdesk.repository.ActivityLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
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
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "start", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(value = "end", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "25") int size,
            Model model
    ) {
        String selectedAction = allowed(action, activityLogRepository.findDistinctActions());
        String selectedEntityType = allowed(entityType, activityLogRepository.findDistinctEntityTypes());
        String trimmedSearch = normalizeSearch(search);
        int pageSize = boundedSize(size);
        if (start != null && end != null && start.isAfter(end)) {
            model.addAttribute("error", "Start date cannot be after end date.");
            model.addAttribute("logs", List.of());
            model.addAttribute("resultCount", 0L);
            model.addAttribute("page", 0);
            model.addAttribute("pageCount", 1);
        } else {
            var result = findPage(selectedAction, selectedEntityType, trimmedSearch, start, end, Math.max(0, page), pageSize);
            if (result.getTotalPages() > 0 && result.getNumber() >= result.getTotalPages())
                result = findPage(selectedAction, selectedEntityType, trimmedSearch, start, end, result.getTotalPages() - 1, pageSize);
            model.addAttribute("logs", result.getContent());
            model.addAttribute("resultCount", result.getTotalElements());
            model.addAttribute("page", result.getNumber());
            model.addAttribute("pageCount", Math.max(1, result.getTotalPages()));
        }

        model.addAttribute("actions", activityLogRepository.findDistinctActions());
        model.addAttribute("entityTypes", activityLogRepository.findDistinctEntityTypes());
        model.addAttribute("selectedAction", selectedAction);
        model.addAttribute("selectedEntityType", selectedEntityType);
        model.addAttribute("search", trimmedSearch == null ? "" : trimmedSearch);
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        model.addAttribute("size", pageSize);
        model.addAttribute("totalEvents", activityLogRepository.count());
        model.addAttribute("todayEvents", activityLogRepository.countByTimestampGreaterThanEqual(LocalDate.now().atStartOfDay()));
        model.addAttribute("actionCount", activityLogRepository.findDistinctActions().size());
        model.addAttribute("securityEvents", activityLogRepository.countSecurityEvents());
        return "admin-activity-logs";
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String action, @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        if (start != null && end != null && start.isAfter(end)) throw new IllegalArgumentException("Start date cannot be after end date.");
        String selectedAction = allowed(action, activityLogRepository.findDistinctActions());
        String selectedEntity = allowed(entityType, activityLogRepository.findDistinctEntityTypes());
        var logs = findPage(selectedAction, selectedEntity, normalizeSearch(search), start, end, 0, 5000).getContent();
        StringBuilder csv = new StringBuilder("Timestamp,Actor ID,Actor Name,Action,Entity Type,Entity ID,IP Address\r\n");
        for (ActivityLog log : logs) {
            csv.append(csv(log.getTimestamp().toString())).append(',')
                    .append(csv(log.getUser() == null ? "System / Anonymous" : log.getUser().getUniversityId())).append(',')
                    .append(csv(log.getUser() == null ? "" : log.getUser().getFirstName() + " " + log.getUser().getLastName())).append(',')
                    .append(csv(log.getAction())).append(',').append(csv(log.getEntityType())).append(',')
                    .append(csv(log.getEntityId() == null ? "" : log.getEntityId().toString())).append(',')
                    .append(csv(log.getIpAddress())).append("\r\n");
        }
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"activity-logs.csv\"")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private org.springframework.data.domain.Page<ActivityLog> findPage(String action, String entityType, String search,
            LocalDate start, LocalDate end, int page, int size) {
        return activityLogRepository.findFilteredLogs(action, entityType, search,
                start == null ? null : start.atStartOfDay(), end == null ? null : end.plusDays(1).atStartOfDay(),
                PageRequest.of(page, size));
    }
    private static int boundedSize(int size) { return List.of(10, 25, 50, 100).contains(size) ? size : 25; }
    private static String normalizeSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String clean = search.trim().replaceAll("\\s+", " ");
        return clean.substring(0, Math.min(150, clean.length()));
    }
    private static String allowed(String value, List<String> allowed) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        return allowed.contains(clean) ? clean : null;
    }
    private static String csv(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
