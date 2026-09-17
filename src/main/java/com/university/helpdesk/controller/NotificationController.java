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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
            Authentication authentication,
            Model model
    ) {
        UserAccount user = getAuthenticatedUser(authentication);

        List<Notification> notifications = "unread".equalsIgnoreCase(filter)
                ? notificationService.getUnreadForUser(user.getUserId())
                : notificationService.getForUser(user.getUserId());

        long unreadCount = notificationService.countUnread(user.getUserId());

        model.addAttribute("notifications", notifications);
        model.addAttribute("unreadCount", unreadCount);
        model.addAttribute("currentFilter", filter.toLowerCase());
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

        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        Long ticketId = ticket.getTicketId();

        // Role-aware ticket redirect:
        if (authorities.contains("ROLE_Student")) {
            // Ensure student only accesses their own ticket
            if (ticket.getStudent() != null &&
                    ticket.getStudent().getUser().getUserId().equals(user.getUserId())) {
                return "redirect:/student/tickets/" + ticketId;
            }
            throw new AccessDeniedException("You cannot access this ticket.");
        }

        if (authorities.contains("ROLE_Help Desk Support Staff") ||
                authorities.contains("ROLE_Department Support Team Member")) {
            return "redirect:/staff/tickets/" + ticketId;
        }

        if (authorities.contains("ROLE_Department Manager")) {
            return "redirect:/manager/tickets/" + ticketId;
        }

        if (authorities.contains("ROLE_System Administrator")) {
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
