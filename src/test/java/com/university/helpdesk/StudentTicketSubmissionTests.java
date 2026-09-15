package com.university.helpdesk;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.AttachmentService;
import com.university.helpdesk.service.CommentService;
import com.university.helpdesk.service.TicketService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class StudentTicketSubmissionTests {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private ServiceRequestRepository serviceRequestRepository;

    @Autowired
    private TicketStatusHistoryRepository historyRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private UserCommentRepository commentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Category itCategory;
    private Category unmappedCategory;
    private UserAccount stu001User;
    private UserAccount stu002User;

    @BeforeEach
    void setUp() {
        // Ensure STU001 exists
        stu001User = userAccountRepository.findByUniversityId("STU001")
                .orElseThrow(() -> new IllegalStateException("STU001 must exist from data initializer."));

        // Setup STU002 as a test student for boundary testing (idempotent for tests)
        stu002User = userAccountRepository.findByUniversityId("STU002").orElseGet(() -> {
            Role studentRole = roleRepository.findByRoleName("Student")
                    .orElseThrow(() -> new IllegalStateException("Student role must exist."));

            UserAccount user = new UserAccount();
            user.setUniversityId("STU002");
            user.setEmail("student2.test@university.edu");
            user.setPasswordHash(passwordEncoder.encode("Student@123"));
            user.setFirstName("Jane");
            user.setLastName("Doe");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user = userAccountRepository.save(user);

            UserRole userRole = new UserRole();
            userRole.setUser(user);
            userRole.setRole(studentRole);
            userRole.setActive(true);
            userRoleRepository.save(userRole);

            Student student = new Student();
            student.setUser(user);
            student.setFaculty("Engineering");
            student.setProgram("Software Engineering");
            student.setAcademicYear(3);
            studentRepository.save(student);

            return user;
        });

        // Setup categories
        itCategory = categoryRepository.findByCategoryName("Password Reset")
                .orElseGet(() -> {
                    Department itDept = departmentRepository.findByDepartmentName("IT Support")
                            .orElseGet(() -> {
                                Department d = new Department();
                                d.setDepartmentName("IT Support");
                                return departmentRepository.save(d);
                            });
                    Category c = new Category();
                    c.setCategoryName("Password Reset");
                    c.setDepartment(itDept);
                    c.setStatus("ACTIVE");
                    return categoryRepository.save(c);
                });

        unmappedCategory = categoryRepository.findByCategoryName("General Inquiry")
                .orElseGet(() -> {
                    Category c = new Category();
                    c.setCategoryName("General Inquiry");
                    c.setDepartment(null);
                    c.setStatus("ACTIVE");
                    return categoryRepository.save(c);
                });
    }

    @Test
    @DisplayName("Submit Incident ticket with initial status NEW, priority MEDIUM, subtype record, history, and notifications")
    void testSubmitIncidentTicketSuccess() {
        Ticket ticket = ticketService.createTicket(
                "STU001",
                itCategory.getCategoryId(),
                TicketType.INCIDENT,
                "Unable to login to student portal",
                "Receiving error 500 when accessing coursework page.",
                "Portal Error",
                "High"
        );

        assertNotNull(ticket);
        assertNotNull(ticket.getTicketId());
        assertNotNull(ticket.getReferenceNo());
        assertTrue(ticket.getReferenceNo().startsWith("HD-"));
        assertEquals(TicketStatus.NEW, ticket.getStatus());
        assertEquals(TicketPriority.MEDIUM, ticket.getPriority());
        assertEquals(TicketType.INCIDENT, ticket.getTicketType());
        assertEquals(itCategory.getCategoryId(), ticket.getCategory().getCategoryId());

        // Verify INCIDENT subtype row
        Incident incident = incidentRepository.findById(ticket.getTicketId()).orElse(null);
        assertNotNull(incident);
        assertEquals("Portal Error", incident.getIncidentType());
        assertEquals("High", incident.getSeverity());

        // Verify initial TICKET_STATUS_HISTORY row
        List<TicketStatusHistory> historyList = historyRepository
                .findByTicketTicketIdOrderByChangedDateAsc(ticket.getTicketId());
        assertFalse(historyList.isEmpty());
        TicketStatusHistory initialHistory = historyList.get(0);
        assertEquals(TicketStatus.NEW, initialHistory.getNewStatus());
        assertNull(initialHistory.getOldStatus());
        assertEquals(stu001User.getUserId(), initialHistory.getChangedByUser().getUserId());

        // Verify Notification to student
        List<Notification> studentNotifications = notificationRepository
                .findByUserUserIdOrderByCreatedDateDesc(stu001User.getUserId());
        boolean hasSubmitNotification = studentNotifications.stream()
                .anyMatch(n -> n.getTicket() != null
                        && n.getTicket().getTicketId().equals(ticket.getTicketId())
                        && n.getNotificationType() == NotificationType.SUBMITTED);
        assertTrue(hasSubmitNotification, "Student must receive ticket submission notification");
    }

    @Test
    @DisplayName("Submit Service Request ticket with subtype record and requested date")
    void testSubmitServiceRequestSuccess() {
        Ticket ticket = ticketService.createTicket(
                "STU001",
                itCategory.getCategoryId(),
                TicketType.SERVICE_REQUEST,
                "Request for statistical software license",
                "Need SPSS license key for final year research project.",
                "SPSS Software License",
                null
        );

        assertNotNull(ticket);
        assertEquals(TicketType.SERVICE_REQUEST, ticket.getTicketType());
        assertEquals(TicketStatus.NEW, ticket.getStatus());

        // Verify SERVICE_REQUEST subtype row
        ServiceRequest request = serviceRequestRepository.findById(ticket.getTicketId()).orElse(null);
        assertNotNull(request);
        assertEquals("SPSS Software License", request.getRequestedService());
        assertNotNull(request.getRequestedDate());
    }

    @Test
    @DisplayName("Category without mapped department allows submission and routes to System Administrator")
    void testUnmappedCategoryRoutesToAdministrator() {
        Ticket ticket = ticketService.createTicket(
                "STU001",
                unmappedCategory.getCategoryId(),
                TicketType.INCIDENT,
                "General question regarding graduation ceremony",
                "Need clarification on attendance guidelines.",
                "General Inquiry",
                "Low"
        );

        assertNotNull(ticket);
        assertNull(ticket.getCategory().getDepartment());
        assertEquals(TicketStatus.NEW, ticket.getStatus());

        // Verify System Administrator received manual routing notification
        UserAccount adminUser = userAccountRepository.findByUniversityId("ADM001").orElse(null);
        assertNotNull(adminUser);

        List<Notification> adminNotifications = notificationRepository
                .findByUserUserIdOrderByCreatedDateDesc(adminUser.getUserId());
        boolean hasAdminAlert = adminNotifications.stream()
                .anyMatch(n -> n.getTicket() != null
                        && n.getTicket().getTicketId().equals(ticket.getTicketId())
                        && n.getNotificationType() == NotificationType.SYSTEM
                        && n.getMessage().contains("manual routing"));
        assertTrue(hasAdminAlert, "Admin must be notified of tickets requiring manual routing");
    }

    @Test
    @DisplayName("Upload valid attachments (PDF, PNG, TXT) under 5 MB")
    void testValidAttachmentStorage() throws IOException {
        Ticket ticket = ticketService.createTicket(
                "STU001",
                itCategory.getCategoryId(),
                TicketType.INCIDENT,
                "Attachment test incident",
                "Testing attachment upload functionality.",
                null,
                null
        );

        // PDF file
        MockMultipartFile pdfFile = new MockMultipartFile(
                "attachment",
                "screenshot_error.pdf",
                "application/pdf",
                "%PDF-1.4 test content".getBytes(StandardCharsets.UTF_8)
        );
        Attachment att1 = attachmentService.store(ticket, pdfFile);
        assertNotNull(att1);
        assertEquals("screenshot_error.pdf", att1.getFileName());
        assertEquals("application/pdf", att1.getFileType());
        assertTrue(att1.getFilePath().endsWith(".pdf"));

        // TXT file
        MockMultipartFile txtFile = new MockMultipartFile(
                "attachment",
                "logs.txt",
                "text/plain",
                "Log trace details".getBytes(StandardCharsets.UTF_8)
        );
        Attachment att2 = attachmentService.store(ticket, txtFile);
        assertNotNull(att2);
        assertTrue(att2.getFilePath().endsWith(".txt"));

        // PNG file
        MockMultipartFile pngFile = new MockMultipartFile(
                "attachment",
                "proof.png",
                "image/png",
                new byte[]{ (byte) 0x89, 0x50, 0x4E, 0x47 }
        );
        Attachment att3 = attachmentService.store(ticket, pngFile);
        assertNotNull(att3);
        assertTrue(att3.getFilePath().endsWith(".png"));

        // Verify DB records
        List<Attachment> list = attachmentRepository.findByTicketTicketIdOrderByUploadedDateAsc(ticket.getTicketId());
        assertEquals(3, list.size());
    }

    @Test
    @DisplayName("Reject invalid attachment types such as executable or script files")
    void testInvalidAttachmentTypeRejected() {
        Ticket ticket = ticketService.createTicket(
                "STU001",
                itCategory.getCategoryId(),
                TicketType.INCIDENT,
                "Malicious file test",
                "Testing file rejection.",
                null,
                null
        );

        MockMultipartFile exeFile = new MockMultipartFile(
                "attachment",
                "virus.exe",
                "application/octet-stream",
                "MZ...".getBytes(StandardCharsets.UTF_8)
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                attachmentService.store(ticket, exeFile)
        );
        assertTrue(ex.getMessage().contains("Only PDF, PNG, JPG/JPEG, and TXT attachments are allowed."));
    }

    @Test
    @DisplayName("Reject attachment exceeding 5 MB limit")
    void testAttachmentExceeding5MBRejected() {
        Ticket ticket = ticketService.createTicket(
                "STU001",
                itCategory.getCategoryId(),
                TicketType.INCIDENT,
                "Large file test",
                "Testing size limit rejection.",
                null,
                null
        );

        // 5 MB + 1 byte
        byte[] oversizedData = new byte[(int) (5L * 1024L * 1024L + 1L)];
        MockMultipartFile bigFile = new MockMultipartFile(
                "attachment",
                "large_dump.pdf",
                "application/pdf",
                oversizedData
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                attachmentService.store(ticket, bigFile)
        );
        assertEquals("Attachment must be 5 MB or smaller.", ex.getMessage());
    }

    @Test
    @DisplayName("Enforce student ticket ownership: STU001 cannot view or download STU002 ticket")
    void testStudentOwnershipEnforcement() throws IOException {
        // Create ticket owned by STU002
        Ticket stu002Ticket = ticketService.createTicket(
                "STU002",
                itCategory.getCategoryId(),
                TicketType.INCIDENT,
                "STU002 Private Ticket",
                "Confidential student record.",
                null,
                null
        );

        MockMultipartFile attachmentFile = new MockMultipartFile(
                "attachment",
                "private_grades.pdf",
                "application/pdf",
                "%PDF-1.4 private".getBytes(StandardCharsets.UTF_8)
        );
        Attachment stu002Attachment = attachmentService.store(stu002Ticket, attachmentFile);

        // STU001 attempts to view STU002 ticket -> must throw AccessDeniedException
        assertThrows(AccessDeniedException.class, () ->
                ticketService.getStudentTicket(stu002Ticket.getTicketId(), "STU001")
        );

        // STU001 list must NOT contain STU002 ticket
        List<Ticket> stu001Tickets = ticketService.getStudentTickets("STU001");
        assertFalse(stu001Tickets.stream().anyMatch(t -> t.getTicketId().equals(stu002Ticket.getTicketId())));

        // STU002 can access their own ticket
        Ticket retrievedByOwner = ticketService.getStudentTicket(stu002Ticket.getTicketId(), "STU002");
        assertNotNull(retrievedByOwner);
        assertEquals(stu002Ticket.getTicketId(), retrievedByOwner.getTicketId());

        // STU002 list contains their ticket
        List<Ticket> stu002Tickets = ticketService.getStudentTickets("STU002");
        assertTrue(stu002Tickets.stream().anyMatch(t -> t.getTicketId().equals(stu002Ticket.getTicketId())));
    }

    @Test
    @DisplayName("Student can add public comments to their ticket")
    void testAddPublicComment() {
        Ticket ticket = ticketService.createTicket(
                "STU001",
                itCategory.getCategoryId(),
                TicketType.INCIDENT,
                "Comment verification ticket",
                "Initial issue description.",
                null,
                null
        );

        UserComment comment = commentService.addComment(
                ticket.getTicketId(),
                "STU001",
                "Follow-up detail from student: Issue occurred on Chrome v120.",
                CommentType.PUBLIC
        );

        assertNotNull(comment);
        assertNotNull(comment.getCommentId());
        assertEquals(CommentType.PUBLIC, comment.getCommentType());
        assertEquals("Follow-up detail from student: Issue occurred on Chrome v120.", comment.getCommentText());

        List<UserComment> comments = commentService.getComments(ticket.getTicketId());
        assertEquals(1, comments.size());
        assertEquals(comment.getCommentId(), comments.get(0).getCommentId());
    }

    @Test
    @DisplayName("Server-side validation rejects invalid submissions")
    void testValidationRejectsInvalidFields() {
        // Missing category
        assertThrows(IllegalArgumentException.class, () ->
                ticketService.createTicket("STU001", null, TicketType.INCIDENT, "Subject", "Desc", null, null)
        );

        // Missing ticket type
        assertThrows(IllegalArgumentException.class, () ->
                ticketService.createTicket("STU001", itCategory.getCategoryId(), null, "Subject", "Desc", null, null)
        );

        // Blank subject
        assertThrows(IllegalArgumentException.class, () ->
                ticketService.createTicket("STU001", itCategory.getCategoryId(), TicketType.INCIDENT, "   ", "Desc", null, null)
        );

        // Blank description
        assertThrows(IllegalArgumentException.class, () ->
                ticketService.createTicket("STU001", itCategory.getCategoryId(), TicketType.INCIDENT, "Subject", "   ", null, null)
        );
    }
}
