package com.university.helpdesk.security;

import org.springframework.security.core.Authentication;

/** Keeps redirects aligned with the shared navigation's role precedence. */
public final class RoleDestinations {
    private RoleDestinations() {}

    public static String basePath(Authentication authentication) {
        if (authentication == null) return "";
        var roles = authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        if (roles.contains("ROLE_System Administrator")) return "/admin";
        if (roles.contains("ROLE_Student")) return "/student";
        if (roles.contains("ROLE_Department Manager")) return "/manager";
        if (roles.contains("ROLE_Help Desk Support Staff") || roles.contains("ROLE_Department Support Team Member")) return "/staff";
        if (roles.contains("ROLE_University Management")) return "/management";
        return "";
    }
}
