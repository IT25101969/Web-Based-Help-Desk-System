package com.university.helpdesk.controller;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import com.university.helpdesk.security.RoleDestinations;


@Controller
public class DashboardController {

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        String base = RoleDestinations.basePath(authentication);
        return base.isEmpty() ? "dashboard" : "redirect:" + base + "/dashboard";
    }

    @GetMapping("/tickets")
    public String tickets(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        String base = RoleDestinations.basePath(authentication);
        if (base.isEmpty() || base.equals("/management")) {
            throw new AccessDeniedException("You do not have permission to view tickets.");
        }
        return "redirect:" + base + "/tickets";
    }
}
