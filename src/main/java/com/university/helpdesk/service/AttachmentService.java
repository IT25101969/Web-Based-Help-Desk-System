package com.university.helpdesk.service;

import com.university.helpdesk.entity.Attachment;
import com.university.helpdesk.entity.Ticket;
import com.university.helpdesk.repository.AttachmentRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService {
    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private static final long MAX_FILE_SIZE = 5L * 1024L * 1024L;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/pjpeg",
            "text/plain"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf",
            ".png",
            ".jpg",
            ".jpeg",
            ".txt"
    );

    private final AttachmentRepository attachmentRepository;
    private final Path uploadRoot;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            @Value("${app.upload-dir:uploads}") String uploadDir
    ) {
        this.attachmentRepository = attachmentRepository;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Transactional(rollbackFor = IOException.class)
    public Attachment store(Ticket ticket, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Attachment must be 5 MB or smaller.");
        }

        String originalName = file.getOriginalFilename() == null
                ? "attachment"
                : Paths.get(file.getOriginalFilename()).getFileName().toString().trim();

        if (originalName.isBlank() || originalName.length() > 255 || originalName.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Attachment name must contain 1 to 255 characters without control characters.");
        }
        String extension = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0 && dot < originalName.length() - 1) {
            extension = originalName.substring(dot).toLowerCase(java.util.Locale.ROOT);
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Only PDF, PNG, JPG/JPEG, and TXT attachments are allowed.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Only PDF, PNG, JPG/JPEG, and TXT attachments are allowed.");
        }

        Files.createDirectories(uploadRoot);

        String storedName = UUID.randomUUID() + extension;
        Path target = uploadRoot.resolve(storedName).normalize();

        if (!target.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid attachment path.");
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) removeFile(target);
                }
            });
        }
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target);
        } catch (IOException ex) {
            removeFile(target);
            throw ex;
        }

        Attachment attachment = new Attachment();
        attachment.setTicket(ticket);
        attachment.setFileName(originalName);
        attachment.setFileType(contentType);
        attachment.setFileSize(file.getSize());
        attachment.setFilePath(storedName);
        return attachmentRepository.save(attachment);
    }

    @Transactional(readOnly = true)
    public Attachment getAttachment(Long id) {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment was not found."));
    }

    public Resource load(Attachment attachment) throws IOException {
        Path path = uploadRoot.resolve(attachment.getFilePath()).normalize();
        if (!path.startsWith(uploadRoot)) {
            throw new SecurityException("Invalid attachment path.");
        }
        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment file is no longer available.");
        }
        return resource;
    }

    @Transactional
    public void deleteAttachment(Long attachmentId, Long ticketId, String studentUniversityId) {
        Attachment attachment = getAttachment(attachmentId);
        if (!attachment.getTicket().getTicketId().equals(ticketId)) {
            throw new IllegalArgumentException("Attachment does not belong to this ticket.");
        }
        if (studentUniversityId != null && attachment.getTicket().getStudent() != null) {
            String ownerId = attachment.getTicket().getStudent().getUser().getUniversityId();
            if (!ownerId.equals(studentUniversityId)) {
                throw new org.springframework.security.access.AccessDeniedException("You are not authorized to delete this attachment.");
            }
        }
        Path path = uploadRoot.resolve(attachment.getFilePath()).normalize();
        if (!path.startsWith(uploadRoot)) {
            throw new SecurityException("Invalid attachment path.");
        }
        attachmentRepository.delete(attachment);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { removeFile(path); }
            });
        } else {
            removeFile(path);
        }
    }

    private void removeFile(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException ex) { log.warn("Could not remove attachment file {}", path.getFileName(), ex); }
    }
}
