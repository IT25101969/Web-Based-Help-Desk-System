package com.university.helpdesk;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.AttachmentRepository;
import com.university.helpdesk.service.AttachmentService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.file.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttachmentLifecycleTests {
    @TempDir Path directory;
    AttachmentRepository repository;
    AttachmentService service;

    @BeforeEach void setup() {
        repository = mock(AttachmentRepository.class);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        service = new AttachmentService(repository, directory.toString());
        TransactionSynchronizationManager.initSynchronization();
    }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test void uploadClosesStreamAndRemovesFileOnRollback() throws Exception {
        boolean[] closed = {false};
        var file = new MockMultipartFile("attachment", "notes.txt", "text/plain", "hello".getBytes()) {
            @Override public InputStream getInputStream() {
                return new ByteArrayInputStream("hello".getBytes()) {
                    @Override public void close() throws IOException { closed[0] = true; super.close(); }
                };
            }
        };
        Attachment attachment = service.store(new Ticket(), file);
        Path stored = directory.resolve(attachment.getFilePath());
        assertTrue(closed[0]); assertTrue(Files.exists(stored));
        for (var callback : TransactionSynchronizationManager.getSynchronizations())
            callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertFalse(Files.exists(stored));
    }

    @Test void rolledBackDeletionKeepsPhysicalFile() throws Exception {
        Attachment attachment = storedAttachment();
        service.deleteAttachment(1L, 2L, "STU001");
        for (var callback : TransactionSynchronizationManager.getSynchronizations())
            callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertTrue(Files.exists(directory.resolve(attachment.getFilePath())));
    }

    @Test void committedDeletionRemovesPhysicalFile() throws Exception {
        Attachment attachment = storedAttachment();
        service.deleteAttachment(1L, 2L, "STU001");
        assertTrue(Files.exists(directory.resolve(attachment.getFilePath())));
        for (var callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCommit();
        assertFalse(Files.exists(directory.resolve(attachment.getFilePath())));
        verify(repository).delete(attachment);
    }

    @Test void missingDownloadIs404() {
        Attachment attachment = new Attachment(); attachment.setFilePath("missing.txt");
        var error = assertThrows(ResponseStatusException.class, () -> service.load(attachment));
        assertEquals(404, error.getStatusCode().value());
    }

    private Attachment storedAttachment() throws Exception {
        Ticket ticket = new Ticket(); ticket.setTicketId(2L);
        UserAccount user = new UserAccount(); user.setUniversityId("STU001");
        Student student = new Student(); student.setUser(user); ticket.setStudent(student);
        Attachment attachment = new Attachment(); attachment.setTicket(ticket); attachment.setFilePath("stored.txt");
        Files.writeString(directory.resolve("stored.txt"), "test");
        when(repository.findById(1L)).thenReturn(Optional.of(attachment));
        return attachment;
    }
}
