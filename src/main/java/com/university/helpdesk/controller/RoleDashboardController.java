package com.university.helpdesk.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RoleDashboardController {

    @GetMapping("/staff/dashboard")
    public String staffDashboard() {
        return "staff-dashboard";
    }

    @GetMapping("/manager/dashboard")
    public String managerDashboard() {
        return "manager-dashboard";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard() {
        return "admin-dashboard";
    }

    @GetMapping("/management/dashboard")
    public String managementDashboard() {
        return "management-dashboard";
    }
}