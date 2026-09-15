package com.university.helpdesk.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ATTACHMENT")
public class Attachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Attachment_ID")
    private Long attachmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Ticket_ID", nullable = false)
    private Ticket ticket;

    @Column(name = "File_Name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "File_Type", length = 100)
    private String fileType;

    @Column(name = "File_Size", nullable = false)
    private Long fileSize;

    @Column(name = "File_Path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "Uploaded_Date", nullable = false)
    private LocalDateTime uploadedDate;

    @PrePersist
    void onCreate() {
        if (uploadedDate == null) uploadedDate = LocalDateTime.now();
    }

    public Long getAttachmentId() { return attachmentId; }
    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public LocalDateTime getUploadedDate() { return uploadedDate; }
}
