package com.university.helpdesk;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
public class NotificationWebMvcTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    private UserAccount studentUser;
    private UserAccount otherUser;
    private Ticket studentTicket;
    private Notification studentNotification;
    private Notification otherNotification;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        studentUser = userAccountRepository.findByUniversityId("STU001").orElseGet(() -> {
            UserAccount u = new UserAccount();
            u.setUniversityId("STU001");
            u.setEmail("stu001@university.edu");
            u.setFirstName("Student");
            u.setLastName("One");
            u.setPasswordHash("hash123");
            u.setAccountStatus(AccountStatus.ACTIVE);
            return userAccountRepository.save(u);
        });

        Student student = studentRepository.findById(studentUser.getUserId()).orElseGet(() -> {
            Student s = new Student();
            s.setUser(studentUser);
            s.setFaculty("Computing");
            s.setProgram("Software Engineering");
            s.setAcademicYear(1);
            return studentRepository.save(s);
        });

        otherUser = userAccountRepository.findByUniversityId("SUP001").orElseGet(() -> {
            UserAccount u = new UserAccount();
            u.setUniversityId("SUP001");
            u.setEmail("sup001@university.edu");
            u.setFirstName("Support");
            u.setLastName("One");
            u.setPasswordHash("hash123");
            u.setAccountStatus(AccountStatus.ACTIVE);
            return userAccountRepository.save(u);
        });

        Category category = categoryRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    Category c = new Category();
                    c.setCategoryName("Web Testing Cat");
                    c.setStatus("ACTIVE");
                    return categoryRepository.save(c);
                });

        studentTicket = new Ticket();
        studentTicket.setReferenceNo("HD-WEB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        studentTicket.setStudent(student);
        studentTicket.setCategory(category);
        studentTicket.setSubject("Web test subject");
        studentTicket.setDescription("Web test description");
        studentTicket.setStatus(TicketStatus.NEW);
        studentTicket.setPriority(TicketPriority.MEDIUM);
        studentTicket.setTicketType(TicketType.INCIDENT);
        studentTicket.setCreatedDate(LocalDateTime.now());
        studentTicket.setUpdatedDate(LocalDateTime.now());
        studentTicket = ticketRepository.save(studentTicket);

        studentNotification = notificationService.notifyUser(
                studentUser,
                studentTicket,
                NotificationType.SUBMITTED,
                "Your ticket was created successfully."
        );

        otherNotification = notificationService.notifyUser(
                otherUser,
                studentTicket,
                NotificationType.ASSIGNED,
                "Ticket assigned to support staff."
        );
    }

    @Test
    @DisplayName("GET /notifications requires authentication")
    void testListNotificationsUnauthenticatedRedirects() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /notifications for authenticated student renders notification center")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student"})
    void testListNotificationsAuthenticatedStudent() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(view().name("notifications"))
                .andExpect(model().attributeExists("notifications"))
                .andExpect(model().attributeExists("unreadCount"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Notifications")));
    }

    @Test
    @DisplayName("POST /notifications/{id}/read for own notification marks read and redirects")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student"})
    void testMarkOwnNotificationReadSuccess() throws Exception {
        mockMvc.perform(post("/notifications/" + studentNotification.getNotificationId() + "/read")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notifications"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("POST /notifications/{id}/read for another user's notification returns 403 Forbidden")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student"})
    void testMarkOtherUserNotificationReadReturns403() throws Exception {
        mockMvc.perform(post("/notifications/" + otherNotification.getNotificationId() + "/read")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /notifications/read-all marks all read and redirects")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student"})
    void testMarkAllReadSuccess() throws Exception {
        mockMvc.perform(post("/notifications/read-all")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notifications"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("GET /notifications/{id}/go for own notification redirects to role-aware ticket view")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student"})
    void testNavigateToOwnTicketSuccess() throws Exception {
        mockMvc.perform(get("/notifications/" + studentNotification.getNotificationId() + "/go"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/tickets/" + studentTicket.getTicketId()));
    }

    @Test
    @DisplayName("GET /notifications/{id}/go for another user's notification returns 403 Forbidden")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student"})
    void testNavigateToOtherUserNotificationReturns403() throws Exception {
        mockMvc.perform(get("/notifications/" + otherNotification.getNotificationId() + "/go"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /notifications/preferences shows preference form")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student"})
    void testShowPreferencesForm() throws Exception {
        mockMvc.perform(get("/notifications/preferences"))
                .andExpect(status().isOk())
                .andExpect(view().name("notification-preferences"))
                .andExpect(model().attributeExists("preference"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Notification Preferences")));
    }

    @Test
    @DisplayName("POST /notifications/preferences updates preferences and redirects")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student"})
    void testUpdatePreferencesSuccess() throws Exception {
        mockMvc.perform(post("/notifications/preferences")
                        .param("inAppEnabled", "true")
                        .param("emailEnabled", "false")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notifications/preferences"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("POST /notifications/preferences without CSRF returns 403")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student"})
    void testUpdatePreferencesWithoutCsrfRejected() throws Exception {
        mockMvc.perform(post("/notifications/preferences")
                        .param("inAppEnabled", "true")
                        .param("emailEnabled", "false"))
                .andExpect(status().isForbidden());
    }
}
