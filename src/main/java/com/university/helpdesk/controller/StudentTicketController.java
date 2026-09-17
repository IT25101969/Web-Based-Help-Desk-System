package com.university.helpdesk.controller;

import com.university.helpdesk.dto.TicketSubmissionForm;
import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.*;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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
            @RequestParam(value = "type", required = false) TicketType type,
            Model model
    ) {
        TicketSubmissionForm form = new TicketSubmissionForm();
        if (categoryId != null) {
            categoryRepository.findById(categoryId)
                    .filter(category -> "ACTIVE".equalsIgnoreCase(category.getStatus()))
                    .ifPresent(category -> form.setCategoryId(category.getCategoryId()));
        }
        form.setTicketType(type == null ? TicketType.INCIDENT : type);

        model.addAttribute("form", form);
        model.addAttribute(
                "categories",
                categoryRepository.findByStatusIgnoreCaseOrderByCategoryNameAsc("ACTIVE")
        );
        model.addAttribute("ticketTypes", TicketType.values());
        return "student-ticket-form";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("form") TicketSubmissionForm form,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute(
                    "categories",
                    categoryRepository.findByStatusIgnoreCaseOrderByCategoryNameAsc("ACTIVE")
            );
            model.addAttribute("ticketTypes", TicketType.values());
            return "student-ticket-form";
        }

        try {
            Ticket ticket = ticketService.createTicketWithAttachment(
                    authentication.getName(),
                    form.getCategoryId(),
                    form.getTicketType(),
                    form.getSubject(),
                    form.getDescription(),
                    form.getSubtypeDetail(),
                    form.getSeverity(), form.getAttachment()
            );

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Ticket " + ticket.getReferenceNo() + " submitted successfully."
            );

            return "redirect:/student/tickets/" + ticket.getTicketId();

        } catch (IllegalArgumentException | IllegalStateException | IOException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute(
                    "categories",
                    categoryRepository.findByStatusIgnoreCaseOrderByCategoryNameAsc("ACTIVE")
            );
            model.addAttribute("ticketTypes", TicketType.values());
            return "student-ticket-form";
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
        model.addAttribute("incident", ticketService.getIncident(ticketId));
        model.addAttribute("serviceRequest", ticketService.getServiceRequest(ticketId));
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
            redirectAttributes.addFlashAttribute("success", "Reply posted successfully.");
        } catch (IllegalArgumentException ex) {
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
        } catch (AccessDeniedException ex) {
            throw ex;
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
            throw new AccessDeniedException("Attachment does not belong to this ticket.");
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

    @PostMapping("/{ticketId}/delete")
    public String delete(
            @PathVariable Long ticketId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            ticketService.deleteStudentTicket(ticketId, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Ticket deleted successfully.");
            return "redirect:/student/tickets";
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/student/tickets/" + ticketId;
        }
    }

    @PostMapping("/{ticketId}/attachments/{attachmentId}/delete")
    public String deleteAttachment(
            @PathVariable Long ticketId,
            @PathVariable Long attachmentId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            attachmentService.deleteAttachment(attachmentId, ticketId, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Attachment removed successfully.");
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/student/tickets/" + ticketId;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxSizeException(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "Attachment must be 5 MB or smaller.");
        return "redirect:/student/tickets/new";
    }
}
