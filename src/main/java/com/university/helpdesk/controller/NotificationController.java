package com.university.helpdesk.controller;

import com.university.helpdesk.entity.Notification;
import com.university.helpdesk.entity.NotificationPreference;
import com.university.helpdesk.entity.Ticket;
import com.university.helpdesk.entity.UserAccount;
import com.university.helpdesk.repository.UserAccountRepository;
import com.university.helpdesk.service.NotificationPreferenceService;
import com.university.helpdesk.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;
    private final UserAccountRepository userAccountRepository;

    public NotificationController(
            NotificationService notificationService,
            NotificationPreferenceService preferenceService,
            UserAccountRepository userAccountRepository
    ) {
        this.notificationService = notificationService;
        this.preferenceService = preferenceService;
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping
    public String list(
            @RequestParam(name = "filter", defaultValue = "all") String filter,
            @RequestParam(required = false) com.university.helpdesk.entity.NotificationType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication,
            Model model
    ) {
        UserAccount user = getAuthenticatedUser(authentication);

        String currentFilter = "unread".equalsIgnoreCase(filter) ? "unread" : "all";
        var notificationPage = notificationService.searchForUser(user.getUserId(), "unread".equals(currentFilter), type, page, size);

        long unreadCount = notificationService.countUnread(user.getUserId());

        model.addAttribute("notifications", notificationPage.getContent());
        model.addAttribute("notificationPage", notificationPage);
        model.addAttribute("selectedType", type);
        model.addAttribute("notificationTypes", com.university.helpdesk.entity.NotificationType.values());
        model.addAttribute("unreadCount", unreadCount);
        model.addAttribute("currentFilter", currentFilter);
        model.addAttribute("user", user);

        return "notifications";
    }

    @PostMapping("/{notificationId}/read")
    public String markRead(
            @PathVariable Long notificationId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        UserAccount user = getAuthenticatedUser(authentication);

        // Ownership enforcement: throws AccessDeniedException if not owned by user
        notificationService.markRead(notificationId, user.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "Notification marked as read.");

        return "redirect:/notifications";
    }

    @PostMapping("/read-all")
    public String markAllRead(
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        UserAccount user = getAuthenticatedUser(authentication);

        int count = notificationService.markAllRead(user.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "Marked " + count + " notifications as read.");

        return "redirect:/notifications";
    }

    @PostMapping("/{notificationId}/delete")
    public String delete(
            @PathVariable Long notificationId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        UserAccount user = getAuthenticatedUser(authentication);

        try {
            notificationService.deleteNotification(notificationId, user.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "Notification deleted successfully.");
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/notifications";
    }

    @PostMapping("/clear-all")
    public String clearAll(
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        UserAccount user = getAuthenticatedUser(authentication);

        int count = notificationService.clearAllForUser(user.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "Cleared all " + count + " notifications.");

        return "redirect:/notifications";
    }

    @GetMapping("/{notificationId}/go")
    public String navigateToTicket(
            @PathVariable Long notificationId,
            Authentication authentication
    ) {
        UserAccount user = getAuthenticatedUser(authentication);

        // Ownership enforcement: validates notification ownership before resolving any ticket redirect
        Notification notification = notificationService.getNotificationForUser(notificationId, user.getUserId());

        // Mark as read automatically when navigating to the ticket
        if (!Boolean.TRUE.equals(notification.getReadStatus())) {
            notificationService.markRead(notificationId, user.getUserId());
        }

        Ticket ticket = notification.getTicket();
        if (ticket == null) {
            return "redirect:/notifications";
        }

        Long ticketId = ticket.getTicketId();
        String base = com.university.helpdesk.security.RoleDestinations.basePath(authentication);

        // Role-aware ticket redirect:
        if (base.equals("/student")) {
            // Ensure student only accesses their own ticket
            if (ticket.getStudent() != null &&
                    ticket.getStudent().getUser().getUserId().equals(user.getUserId())) {
                return "redirect:/student/tickets/" + ticketId;
            }
            throw new AccessDeniedException("You cannot access this ticket.");
        }

        if (base.equals("/staff")) {
            return "redirect:/staff/tickets/" + ticketId;
        }

        if (base.equals("/manager")) {
            return "redirect:/manager/tickets/" + ticketId;
        }

        if (base.equals("/admin")) {
            return "redirect:/admin/tickets/" + ticketId;
        }

        return "redirect:/dashboard";
    }

    @GetMapping("/preferences")
    public String showPreferences(
            Authentication authentication,
            Model model
    ) {
        UserAccount user = getAuthenticatedUser(authentication);
        NotificationPreference preference = preferenceService.getOrCreatePreference(user.getUserId());
        long unreadCount = notificationService.countUnread(user.getUserId());

        model.addAttribute("preference", preference);
        model.addAttribute("unreadCount", unreadCount);
        model.addAttribute("user", user);

        return "notification-preferences";
    }

    @PostMapping("/preferences")
    public String updatePreferences(
            @RequestParam(name = "inAppEnabled", defaultValue = "false") boolean inAppEnabled,
            @RequestParam(name = "emailEnabled", defaultValue = "false") boolean emailEnabled,
            HttpServletRequest request,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        UserAccount user = getAuthenticatedUser(authentication);

        preferenceService.updatePreference(
                user.getUserId(),
                inAppEnabled,
                emailEnabled,
                request.getRemoteAddr()
        );

        redirectAttributes.addFlashAttribute("successMessage", "Notification preferences updated successfully.");
        return "redirect:/notifications/preferences";
    }

    private UserAccount getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated.");
        }
        return userAccountRepository
                .findByUniversityId(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user was not found."));
    }
}
