package com.university.helpdesk.controller;

import com.university.helpdesk.dto.MonitoringSummary;
import com.university.helpdesk.service.AdminMonitoringService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/monitoring")
public class AdminMonitoringController {

    private final AdminMonitoringService adminMonitoringService;

    public AdminMonitoringController(AdminMonitoringService adminMonitoringService) {
        this.adminMonitoringService = adminMonitoringService;
    }

    @GetMapping
    public String monitoring(Model model) {
        MonitoringSummary summary = adminMonitoringService.getMonitoringSummary();
        model.addAttribute("summary", summary);
        return "admin-monitoring";
    }
}
