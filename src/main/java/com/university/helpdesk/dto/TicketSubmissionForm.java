package com.university.helpdesk.dto;

import com.university.helpdesk.entity.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public class TicketSubmissionForm {

    @NotNull(message = "Please select a category.")
    private Long categoryId;

    @NotNull(message = "Please select a ticket type.")
    private TicketType ticketType;

    @NotBlank(message = "Subject is required.")
    @Size(max = 200, message = "Subject cannot exceed 200 characters.")
    private String subject;

    @NotBlank(message = "Description is required.")
    private String description;

    @Size(max = 150, message = "Subtype detail cannot exceed 150 characters.")
    private String subtypeDetail;

    private String severity;

    private MultipartFile attachment;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public TicketType getTicketType() {
        return ticketType;
    }

    public void setTicketType(TicketType ticketType) {
        this.ticketType = ticketType;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSubtypeDetail() {
        return subtypeDetail;
    }

    public void setSubtypeDetail(String subtypeDetail) {
        this.subtypeDetail = subtypeDetail;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public MultipartFile getAttachment() {
        return attachment;
    }

    public void setAttachment(MultipartFile attachment) {
        this.attachment = attachment;
    }
}
