package com.university.helpdesk.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccessDeniedController {

    @org.springframework.web.bind.annotation.RequestMapping("/access-denied")
    public String accessDenied(jakarta.servlet.http.HttpServletResponse response) {
        response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
        return "access-denied";
    }
}
