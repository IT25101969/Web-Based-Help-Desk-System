package com.university.helpdesk.controller;

import com.university.helpdesk.entity.UserAccount;
import com.university.helpdesk.repository.UserAccountRepository;
import com.university.helpdesk.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserAccountRepository userAccountRepository;

    public NotificationController(
            NotificationService notificationService,
            UserAccountRepository userAccountRepository
    ) {
        this.notificationService = notificationService;
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping
    public String list(Authentication authentication, Model model) {
        UserAccount user = userAccountRepository
                .findByUniversityId(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));

        model.addAttribute(
                "notifications",
                notificationService.getForUser(user.getUserId())
        );

        return "notifications";
    }

    @PostMapping("/{notificationId}/read")
    public String markRead(
            @PathVariable Long notificationId,
            Authentication authentication
    ) {
        UserAccount user = userAccountRepository
                .findByUniversityId(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));

        notificationService.markRead(notificationId, user.getUserId());
        return "redirect:/notifications";
    }
}
