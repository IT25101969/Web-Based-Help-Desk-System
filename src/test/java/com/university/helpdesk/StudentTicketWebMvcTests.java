package com.university.helpdesk;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.AttachmentService;
import com.university.helpdesk.service.TicketService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class StudentTicketWebMvcTests extends com.university.helpdesk.TestAccounts {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private AttachmentService attachmentService;

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
    private PasswordEncoder passwordEncoder;

    private Category activeCategory;
    private Ticket stu001Ticket;
    private Ticket stu002Ticket;
    private Attachment stu002Attachment;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // Ensure STU002 exists for URL tampering tests
        userAccountRepository.findByUniversityId("STU002").orElseGet(() -> {
            Role studentRole = roleRepository.findByRoleName("Student").orElseThrow();
            UserAccount user = new UserAccount();
            user.setUniversityId("STU002");
            user.setEmail("student2@university.edu");
            user.setPasswordHash(passwordEncoder.encode("Student@123"));
            user.setFirstName("Alice");
            user.setLastName("Student");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user = userAccountRepository.save(user);

            UserRole userRole = new UserRole();
            userRole.setUser(user);
            userRole.setRole(studentRole);
            userRole.setActive(true);
            userRoleRepository.save(userRole);

            Student student = new Student();
            student.setUser(user);
            student.setFaculty("Computing");
            student.setProgram("Computer Science");
            student.setAcademicYear(2);
            studentRepository.save(student);

            return user;
        });

        activeCategory = categoryRepository.findByCategoryName("Password Reset")
                .orElseGet(() -> {
                    Department itDept = departmentRepository.findByDepartmentName("IT Support").orElseGet(() -> { Department d = new Department(); d.setDepartmentName("IT Support"); return departmentRepository.save(d); });
                    Category c = new Category();
                    c.setCategoryName("Password Reset");
                    c.setDepartment(itDept);
                    c.setStatus("ACTIVE");
                    return categoryRepository.save(c);
                });

        // STU001 Ticket
        stu001Ticket = ticketService.createTicket(
                "STU001",
                activeCategory.getCategoryId(),
                TicketType.INCIDENT,
                "STU001 Incident",
                "Incident description for STU001",
                "Wi-Fi Error",
                "Medium"
        );

        // STU002 Ticket
        stu002Ticket = ticketService.createTicket(
                "STU002",
                activeCategory.getCategoryId(),
                TicketType.INCIDENT,
                "STU002 Private Incident",
                "Confidential description for STU002",
                "Account Lockout",
                "High"
        );

        MockMultipartFile file = new MockMultipartFile(
                "attachment",
                "secret.pdf",
                "application/pdf",
                "%PDF-1.4 confidential".getBytes(StandardCharsets.UTF_8)
        );
        stu002Attachment = attachmentService.store(stu002Ticket, file);
    }

    @Test
    @DisplayName("GET /student/tickets returns 200 with tickets list for authorized student")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student", "SUBMIT_TICKET", "VIEW_OWN_TICKETS"})
    void testGetStudentTicketsList() throws Exception {
        mockMvc.perform(get("/student/tickets"))
                .andExpect(status().isOk())
                .andExpect(view().name("student-ticket-list"))
                .andExpect(model().attributeExists("tickets"));
    }

    @Test
    @DisplayName("GET /student/tickets/new returns submission form with categories and ticket types")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student", "SUBMIT_TICKET", "VIEW_OWN_TICKETS"})
    void testGetTicketForm() throws Exception {
        mockMvc.perform(get("/student/tickets/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("student-ticket-form"))
                .andExpect(model().attributeExists("form", "categories", "ticketTypes"));
    }

    @Test
    @DisplayName("POST /student/tickets creates ticket and redirects to view page")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student", "SUBMIT_TICKET", "VIEW_OWN_TICKETS"})
    void testPostTicketSubmission() throws Exception {
        MockMultipartFile attachment = new MockMultipartFile(
                "attachment",
                "test.txt",
                "text/plain",
                "Hello test attachment".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/student/tickets")
                        .file(attachment)
                        .param("categoryId", String.valueOf(activeCategory.getCategoryId()))
                        .param("ticketType", "INCIDENT")
                        .param("subject", "MockMvc Test Incident")
                        .param("description", "MockMvc description of the issue")
                        .param("subtypeDetail", "Network Glitch")
                        .param("severity", "Low")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    @DisplayName("POST /student/tickets with invalid fields returns form with error messages")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student", "SUBMIT_TICKET", "VIEW_OWN_TICKETS"})
    void testPostTicketSubmissionValidationFailure() throws Exception {
        mockMvc.perform(post("/student/tickets")
                        .param("categoryId", "")
                        .param("ticketType", "INCIDENT")
                        .param("subject", "")
                        .param("description", "")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("student-ticket-form"))
                .andExpect(model().hasErrors());
    }

    @Test
    @DisplayName("GET /student/tickets/{id} views owned ticket")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student", "SUBMIT_TICKET", "VIEW_OWN_TICKETS"})
    void testViewOwnTicketSuccess() throws Exception {
        mockMvc.perform(get("/student/tickets/{id}", stu001Ticket.getTicketId()))
                .andExpect(status().isOk())
                .andExpect(view().name("student-ticket-view"))
                .andExpect(model().attributeExists("ticket", "history", "attachments", "comments"));
    }

    @Test
    @DisplayName("GET /student/tickets/{id} denies access when STU001 attempts to view STU002 ticket")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student", "SUBMIT_TICKET", "VIEW_OWN_TICKETS"})
    void testViewOtherStudentTicketDenied() throws Exception {
        mockMvc.perform(get("/student/tickets/{id}", stu002Ticket.getTicketId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET attachment denies access when STU001 attempts to download STU002 attachment")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student", "SUBMIT_TICKET", "VIEW_OWN_TICKETS"})
    void testDownloadOtherStudentAttachmentDenied() throws Exception {
        mockMvc.perform(get("/student/tickets/{tid}/attachments/{aid}",
                        stu002Ticket.getTicketId(), stu002Attachment.getAttachmentId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /student/tickets/{id}/comments posts comment to own ticket")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student", "SUBMIT_TICKET", "VIEW_OWN_TICKETS"})
    void testPostCommentSuccess() throws Exception {
        mockMvc.perform(post("/student/tickets/{id}/comments", stu001Ticket.getTicketId())
                        .param("comment", "Student follow-up note.")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/tickets/" + stu001Ticket.getTicketId()))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    @DisplayName("GET /student/dashboard displays statistics and profile")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student", "VIEW_OWN_TICKETS"})
    void testStudentDashboard() throws Exception {
        mockMvc.perform(get("/student/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("student-dashboard"))
                .andExpect(model().attributeExists("totalTickets", "openTickets", "resolvedTickets", "unreadNotifications"));
    }
}
