package com.university.helpdesk.controller;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Collection;

@Controller
public class DashboardController {

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        for (GrantedAuthority authority : authorities) {
            String role = authority.getAuthority();

            if ("ROLE_Student".equals(role)) {
                return "redirect:/student/dashboard";
            }
            if ("ROLE_Help Desk Support Staff".equals(role) ||
                    "ROLE_Department Support Team Member".equals(role)) {
                return "redirect:/staff/dashboard";
            }
            if ("ROLE_Department Manager".equals(role)) {
                return "redirect:/manager/dashboard";
            }
            if ("ROLE_System Administrator".equals(role)) {
                return "redirect:/admin/dashboard";
            }
            if ("ROLE_University Management".equals(role)) {
                return "redirect:/management/dashboard";
            }
        }

        return "dashboard";
    }

    @GetMapping("/tickets")
    public String tickets(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        for (GrantedAuthority authority : authorities) {
            String role = authority.getAuthority();

            if ("ROLE_Student".equals(role)) {
                return "redirect:/student/tickets";
            }
            if ("ROLE_Help Desk Support Staff".equals(role) ||
                    "ROLE_Department Support Team Member".equals(role)) {
                return "redirect:/staff/tickets";
            }
            if ("ROLE_Department Manager".equals(role)) {
                return "redirect:/manager/tickets";
            }
            if ("ROLE_System Administrator".equals(role)) {
                return "redirect:/admin/tickets";
            }
        }

        throw new AccessDeniedException("You do not have permission to view tickets.");
    }
}