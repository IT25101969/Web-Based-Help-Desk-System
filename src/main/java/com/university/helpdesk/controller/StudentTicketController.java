package com.university.helpdesk.controller;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.*;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/student/tickets")
public class StudentTicketController {

    private final TicketService ticketService;
    private final CategoryRepository categoryRepository;
    private final TicketStatusHistoryRepository historyRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentService attachmentService;
    private final CommentService commentService;
    private final FeedbackService feedbackService;

    public StudentTicketController(
            TicketService ticketService,
            CategoryRepository categoryRepository,
            TicketStatusHistoryRepository historyRepository,
            AttachmentRepository attachmentRepository,
            AttachmentService attachmentService,
            CommentService commentService,
            FeedbackService feedbackService
    ) {
        this.ticketService = ticketService;
        this.categoryRepository = categoryRepository;
        this.historyRepository = historyRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentService = attachmentService;
        this.commentService = commentService;
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public String list(Authentication authentication, Model model) {
        model.addAttribute(
                "tickets",
                ticketService.getStudentTickets(authentication.getName())
        );
        return "student-ticket-list";
    }

    @GetMapping("/new")
    public String createForm(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            Model model
    ) {
        model.addAttribute(
                "categories",
                categoryRepository.findByStatusIgnoreCaseOrderByCategoryNameAsc("ACTIVE")
        );
        model.addAttribute("ticketTypes", TicketType.values());
        model.addAttribute("selectedCategoryId", categoryId);
        return "student-ticket-form";
    }

    @PostMapping
    public String create(
            @RequestParam Long categoryId,
            @RequestParam TicketType ticketType,
            @RequestParam String subject,
            @RequestParam String description,
            @RequestParam(value = "subtypeDetail", required = false) String subtypeDetail,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "attachment", required = false) MultipartFile attachment,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Ticket ticket = ticketService.createTicket(
                    authentication.getName(),
                    categoryId,
                    ticketType,
                    subject,
                    description,
                    subtypeDetail,
                    severity
            );

            attachmentService.store(ticket, attachment);

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Ticket " + ticket.getReferenceNo() + " submitted successfully."
            );

            return "redirect:/student/tickets/" + ticket.getTicketId();

        } catch (IllegalArgumentException | IllegalStateException | IOException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/student/tickets/new";
        }
    }

    @GetMapping("/{ticketId}")
    public String view(
            @PathVariable Long ticketId,
            Authentication authentication,
            Model model
    ) {
        Ticket ticket = ticketService.getStudentTicket(
                ticketId,
                authentication.getName()
        );

        model.addAttribute("ticket", ticket);
        model.addAttribute(
                "history",
                historyRepository.findByTicketTicketIdOrderByChangedDateAsc(ticketId)
        );
        model.addAttribute(
                "attachments",
                attachmentRepository.findByTicketTicketIdOrderByUploadedDateAsc(ticketId)
        );

        List<UserComment> publicComments = commentService.getPublicComments(ticketId);

        model.addAttribute("comments", publicComments);
        model.addAttribute("feedback", feedbackService.findForTicket(ticketId).orElse(null));
        model.addAttribute(
                "canSubmitFeedback",
                (ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.CLOSED)
                        && feedbackService.findForTicket(ticketId).isEmpty()
        );
        return "student-ticket-view";
    }

    @PostMapping("/{ticketId}/comments")
    public String comment(
            @PathVariable Long ticketId,
            @RequestParam String comment,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            ticketService.getStudentTicket(ticketId, authentication.getName());
            commentService.addComment(
                    ticketId,
                    authentication.getName(),
                    comment,
                    CommentType.PUBLIC
            );
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/student/tickets/" + ticketId;
    }

    @PostMapping("/{ticketId}/feedback")
    public String feedback(
            @PathVariable Long ticketId,
            @RequestParam int rating,
            @RequestParam(value = "feedbackComment", required = false) String feedbackComment,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            feedbackService.submit(
                    ticketId,
                    authentication.getName(),
                    rating,
                    feedbackComment
            );
            redirectAttributes.addFlashAttribute("success", "Thank you for your feedback.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/student/tickets/" + ticketId;
    }

    @GetMapping("/{ticketId}/attachments/{attachmentId}")
    public ResponseEntity<Resource> download(
            @PathVariable Long ticketId,
            @PathVariable Long attachmentId,
            Authentication authentication
    ) throws IOException {

        ticketService.getStudentTicket(ticketId, authentication.getName());

        Attachment attachment = attachmentService.getAttachment(attachmentId);

        if (!attachment.getTicket().getTicketId().equals(ticketId)) {
            throw new SecurityException("Attachment does not belong to this ticket.");
        }

        Resource resource = attachmentService.load(attachment);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(attachment.getFileType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(attachment.getFileName())
                                .build()
                                .toString()
                )
                .body(resource);
    }
}
