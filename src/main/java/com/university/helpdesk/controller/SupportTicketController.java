package com.university.helpdesk.controller;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.AttachmentRepository;
import com.university.helpdesk.repository.TicketStatusHistoryRepository;
import com.university.helpdesk.service.*;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SupportTicketController {

    private final TicketService ticketService;
    private final TicketAssignmentService assignmentService;
    private final TicketStatusHistoryRepository historyRepository;
    private final AttachmentRepository attachmentRepository;
    private final CommentService commentService;

    public SupportTicketController(
            TicketService ticketService,
            TicketAssignmentService assignmentService,
            TicketStatusHistoryRepository historyRepository,
            AttachmentRepository attachmentRepository,
            CommentService commentService
    ) {
        this.ticketService = ticketService;
        this.assignmentService = assignmentService;
        this.historyRepository = historyRepository;
        this.attachmentRepository = attachmentRepository;
        this.commentService = commentService;
    }

    @GetMapping({"/staff/tickets", "/manager/tickets", "/admin/tickets"})
    public String list(
            Authentication authentication,
            HttpServletRequest request,
            Model model
    ) {
        String basePath = basePath(request);

        if ("/admin".equals(basePath)) {
            model.addAttribute("tickets", ticketService.getAllTickets());
        } else {
            model.addAttribute(
                    "tickets",
                    ticketService.getSupportTickets(authentication.getName())
            );
        }

        model.addAttribute("basePath", basePath);
        return "support-ticket-list";
    }

    @GetMapping({
            "/staff/tickets/{ticketId}",
            "/manager/tickets/{ticketId}",
            "/admin/tickets/{ticketId}"
    })
    public String view(
            @PathVariable Long ticketId,
            HttpServletRequest request,
            Model model
    ) {
        model.addAttribute("ticket", ticketService.getTicket(ticketId));
        model.addAttribute(
                "history",
                historyRepository.findByTicketTicketIdOrderByChangedDateAsc(ticketId)
        );
        model.addAttribute(
                "assignments",
                assignmentService.getHistory(ticketId)
        );
        model.addAttribute(
                "attachments",
                attachmentRepository.findByTicketTicketIdOrderByUploadedDateAsc(ticketId)
        );
        model.addAttribute(
                "comments",
                commentService.getComments(ticketId)
        );
        model.addAttribute(
                "candidates",
                assignmentService.getCandidates(ticketId)
        );
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("priorities", TicketPriority.values());
        model.addAttribute("commentTypes", CommentType.values());
        model.addAttribute("basePath", basePath(request));
        return "support-ticket-view";
    }

    @PostMapping({
            "/staff/tickets/{ticketId}/assign",
            "/manager/tickets/{ticketId}/assign",
            "/admin/tickets/{ticketId}/assign"
    })
    public String assign(
            @PathVariable Long ticketId,
            @RequestParam Long assignedToUserId,
            @RequestParam TicketPriority priority,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            assignmentService.assign(
                    ticketId,
                    assignedToUserId,
                    authentication.getName(),
                    priority
            );
            redirectAttributes.addFlashAttribute("success", "Ticket assignment updated.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:" + basePath(request) + "/tickets/" + ticketId;
    }

    @PostMapping({
            "/staff/tickets/{ticketId}/status",
            "/manager/tickets/{ticketId}/status",
            "/admin/tickets/{ticketId}/status"
    })
    public String updateStatus(
            @PathVariable Long ticketId,
            @RequestParam TicketStatus status,
            @RequestParam(value = "reason", required = false) String reason,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            ticketService.changeStatus(
                    ticketId,
                    status,
                    authentication.getName(),
                    reason
            );
            redirectAttributes.addFlashAttribute("success", "Ticket status updated.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:" + basePath(request) + "/tickets/" + ticketId;
    }

    @PostMapping({
            "/staff/tickets/{ticketId}/comments",
            "/manager/tickets/{ticketId}/comments",
            "/admin/tickets/{ticketId}/comments"
    })
    public String comment(
            @PathVariable Long ticketId,
            @RequestParam String comment,
            @RequestParam(defaultValue = "PUBLIC") CommentType commentType,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            commentService.addComment(
                    ticketId,
                    authentication.getName(),
                    comment,
                    commentType
            );
            redirectAttributes.addFlashAttribute("success", "Comment added.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:" + basePath(request) + "/tickets/" + ticketId;
    }

    private String basePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/manager/")) return "/manager";
        if (uri.startsWith("/admin/")) return "/admin";
        return "/staff";
    }
}
