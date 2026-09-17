package com.university.helpdesk.controller;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.AttachmentRepository;
import com.university.helpdesk.repository.DepartmentRepository;
import com.university.helpdesk.repository.TicketAssignmentRepository;
import com.university.helpdesk.repository.TicketStatusHistoryRepository;
import com.university.helpdesk.service.*;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.*;

@Controller
public class SupportTicketController {

    private final TicketService ticketService;
    private final TicketAssignmentService assignmentService;
    private final TicketStatusHistoryRepository historyRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentService attachmentService;
    private final CommentService commentService;
    private final DepartmentRepository departmentRepository;
    private final TicketAssignmentRepository assignmentRepository;

    public SupportTicketController(
            TicketService ticketService,
            TicketAssignmentService assignmentService,
            TicketStatusHistoryRepository historyRepository,
            AttachmentRepository attachmentRepository,
            AttachmentService attachmentService,
            CommentService commentService,
            DepartmentRepository departmentRepository,
            TicketAssignmentRepository assignmentRepository
    ) {
        this.ticketService = ticketService;
        this.assignmentService = assignmentService;
        this.historyRepository = historyRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentService = attachmentService;
        this.commentService = commentService;
        this.departmentRepository = departmentRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @GetMapping({"/staff/tickets", "/manager/tickets", "/admin/tickets"})
    public String list(
            @RequestParam(value = "status", required = false) TicketStatus status,
            @RequestParam(value = "priority", required = false) TicketPriority priority,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "unmappedOnly", required = false, defaultValue = "false") boolean unmappedOnly,
            @RequestParam(value = "assignmentState", required = false) String assignmentState,
            Authentication authentication,
            HttpServletRequest request,
            Model model
    ) {
        String basePath = basePath(request);
        List<Ticket> tickets;

        if ("/admin".equals(basePath)) {
            tickets = ticketService.getAdminTicketsWithFilters(departmentId, unmappedOnly, status, priority);
        } else {
            tickets = ticketService.getSupportTicketsWithFilters(authentication.getName(), status, priority);
        }

        Map<Long, TicketAssignment> assigneeMap = new HashMap<>();
        if (!tickets.isEmpty()) {
            List<TicketAssignment> activeAssignments = assignmentRepository.findByTicketInAndStatus(tickets, AssignmentStatus.ACTIVE);
            for (TicketAssignment a : activeAssignments) {
                assigneeMap.put(a.getTicket().getTicketId(), a);
            }
        }

        if ("ASSIGNED".equalsIgnoreCase(assignmentState)) {
            tickets = tickets.stream().filter(t -> assigneeMap.containsKey(t.getTicketId())).toList();
        } else if ("UNASSIGNED".equalsIgnoreCase(assignmentState)) {
            tickets = tickets.stream().filter(t -> !assigneeMap.containsKey(t.getTicketId())).toList();
        }

        model.addAttribute("tickets", tickets);
        model.addAttribute("assigneeMap", assigneeMap);
        model.addAttribute("basePath", basePath);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedPriority", priority);
        model.addAttribute("selectedDepartmentId", departmentId);
        model.addAttribute("selectedUnmappedOnly", unmappedOnly);
        model.addAttribute("selectedAssignmentState", assignmentState);
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("priorities", TicketPriority.values());
        model.addAttribute("departments", departmentRepository.findAll());

        return "support-ticket-list";
    }

    @GetMapping({
            "/staff/tickets/{ticketId}",
            "/manager/tickets/{ticketId}",
            "/admin/tickets/{ticketId}"
    })
    public String view(
            @PathVariable Long ticketId,
            Authentication authentication,
            HttpServletRequest request,
            Model model
    ) {
        Ticket ticket = ticketService.getAuthorizedSupportTicket(ticketId, authentication.getName());
        model.addAttribute("ticket", ticket);
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
                assignmentService.getCandidates(ticketId, authentication.getName())
        );

        boolean canAssign = ticket.getCategory() != null
                && ticket.getCategory().getDepartment() != null
                && ticket.getStatus() != TicketStatus.CLOSED;
        model.addAttribute("canAssign", canAssign);

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
        } catch (AccessDeniedException ex) {
            throw ex;
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
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:" + basePath(request) + "/tickets/" + ticketId;
    }

    @PostMapping({
            "/staff/tickets/{ticketId}/priority",
            "/manager/tickets/{ticketId}/priority",
            "/admin/tickets/{ticketId}/priority"
    })
    public String updatePriority(
            @PathVariable Long ticketId,
            @RequestParam TicketPriority priority,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            ticketService.updatePriority(
                    ticketId,
                    priority,
                    authentication.getName()
            );
            redirectAttributes.addFlashAttribute("success", "Ticket priority updated.");
        } catch (AccessDeniedException ex) {
            throw ex;
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
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:" + basePath(request) + "/tickets/" + ticketId;
    }

    @PostMapping({
            "/staff/tickets/{ticketId}/comments/{commentId}/delete",
            "/manager/tickets/{ticketId}/comments/{commentId}/delete",
            "/admin/tickets/{ticketId}/comments/{commentId}/delete"
    })
    public String deleteComment(
            @PathVariable Long ticketId,
            @PathVariable Long commentId,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            commentService.deleteComment(commentId, ticketId, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Comment deleted successfully.");
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:" + basePath(request) + "/tickets/" + ticketId;
    }

    @PostMapping({
            "/staff/tickets/{ticketId}/unassign",
            "/manager/tickets/{ticketId}/unassign",
            "/admin/tickets/{ticketId}/unassign"
    })
    public String unassign(
            @PathVariable Long ticketId,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            assignmentService.unassign(ticketId, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Assignment removed successfully.");
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:" + basePath(request) + "/tickets/" + ticketId;
    }

    @GetMapping({
            "/staff/tickets/{ticketId}/attachments/{attachmentId}",
            "/manager/tickets/{ticketId}/attachments/{attachmentId}",
            "/admin/tickets/{ticketId}/attachments/{attachmentId}"
    })
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long ticketId,
            @PathVariable Long attachmentId,
            Authentication authentication
    ) throws IOException {
        Ticket ticket = ticketService.getAuthorizedSupportTicket(ticketId, authentication.getName());
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment was not found."));

        if (!attachment.getTicket().getTicketId().equals(ticket.getTicketId())) {
            throw new IllegalArgumentException("Attachment does not belong to this ticket.");
        }

        Resource resource = attachmentService.load(attachment);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(attachment.getFileType()))
                .body(resource);
    }

    private String basePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/manager/")) return "/manager";
        if (uri.startsWith("/admin/")) return "/admin";
        return "/staff";
    }
}
