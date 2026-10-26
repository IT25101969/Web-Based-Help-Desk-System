package com.university.helpdesk;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class FeatureEdgeCaseTests extends TestAccounts {
    @Autowired WebApplicationContext context;
    @Autowired PasswordResetService resets;
    @Autowired TicketService tickets;
    @Autowired CategoryRepository categories;
    @Autowired DepartmentRepository departments;
    @Autowired UserAccountRepository users;
    @Autowired UserDepartmentRepository memberships;
    @Autowired UserRoleRepository roles;
    @Autowired EmailNotificationQueueRepository emails;
    @Autowired NotificationRepository notifications;
    @Autowired AttachmentService attachments;
    MockMvc mvc;
    @BeforeEach void mvc() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    Ticket ticket(Department department) {
        var c = new Category(); c.setCategoryName("Edge " + UUID.randomUUID()); c.setDepartment(department); categories.save(c);
        return tickets.createTicket("STU001", c.getCategoryId(), TicketType.INCIDENT, "Edge case", "Details", "Network", "MEDIUM");
    }

    @Test void missingNotificationLinksAndReadActionsReturnNotFound() throws Exception {
        var student = user("STU001").roles("Student");
        mvc.perform(get("/notifications/9223372036854775807/go").with(student)).andExpect(status().isNotFound());
        mvc.perform(post("/notifications/9223372036854775807/read").with(student).with(csrf())).andExpect(status().isNotFound());
    }

    @Test void multibytePasswordAboveBcryptLimitShowsValidationAndLeavesTokenUsable() throws Exception {
        String token = resets.createResetToken("STU001", "127.0.0.1").orElseThrow();
        String password = "界".repeat(25);
        mvc.perform(post("/reset-password").with(csrf()).param("token", token)
                        .param("password", password).param("confirmPassword", password))
                .andExpect(status().isOk()).andExpect(model().attributeExists("error"));
        assertTrue(resets.isValidToken(token));
    }

    @Test void passwordResetServiceRejectsShortPasswords() {
        String token = resets.createResetToken("STU001", "127.0.0.1").orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> resets.resetPassword(token, "short", "127.0.0.1"));
        assertTrue(resets.isValidToken(token));
    }

    @Test void staleDepartmentMembershipDoesNotReceiveNewTicketDetails() {
        var d = new Department(); d.setDepartmentName("Edge " + UUID.randomUUID()); departments.save(d);
        var support = users.findByUniversityId("SUP001").orElseThrow();
        var member = new UserDepartment(); member.setUser(support); member.setDepartment(d); memberships.save(member);
        var grants = roles.findByUserUserIdAndActiveTrue(support.getUserId());
        grants.forEach(r -> r.setActive(false)); roles.saveAll(grants);
        ticket(d);
        assertTrue(emails.findByUserUserIdOrderByCreatedAtDesc(support.getUserId()).isEmpty());
        assertTrue(notifications.findByUserUserIdOrderByCreatedDateDesc(support.getUserId()).isEmpty());
    }

    @Test void attachmentNameTooLongIsValidatedBeforeSqlWrite() {
        var t = ticket(null);
        var file = new MockMultipartFile("attachment", "a".repeat(252) + ".txt", "text/plain", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> attachments.store(t, file));
    }

    @Test void unicodeDownloadNamesUseEncodedHttpHeadersForStudentAndStaff() throws Exception {
        var t = ticket(null);
        var file = new MockMultipartFile("attachment", "විස්තර.txt", "text/plain", "Details".getBytes());
        var attachment = attachments.store(t, file);
        for (String role : new String[]{"student", "admin"}) {
            String account = role.equals("student") ? "STU001" : "ADM001";
            String authority = role.equals("student") ? "ROLE_Student" : "ROLE_System Administrator";
            String permission = role.equals("student") ? "VIEW_OWN_TICKETS" : "VIEW_ALL_TICKETS";
            String header = mvc.perform(get("/" + role + "/tickets/" + t.getTicketId() + "/attachments/" + attachment.getAttachmentId())
                            .with(user(account).authorities(() -> authority, () -> permission)))
                    .andExpect(status().isOk()).andReturn().getResponse().getHeader("Content-Disposition");
            assertNotNull(header);
            assertTrue(header.chars().allMatch(c -> c < 128), "HTTP headers must encode Unicode filenames");
            assertTrue(header.contains("filename*=UTF-8''"));
        }
    }
}
