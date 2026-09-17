package com.university.helpdesk;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
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
class IntegrationRegressionTests extends TestAccounts {
    @Autowired WebApplicationContext context;
    @Autowired UserAccountRepository accounts;
    @Autowired CategoryRepository categories;
    @Autowired TicketRepository tickets;
    @Autowired PasswordEncoder encoder;
    @Autowired PasswordResetService resets;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired FaqRepository faqs;
    MockMvc mvc;

    @BeforeEach void setupMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void reportAliasesRequireReportPermission() throws Exception {
        for (String path : new String[]{"/reports", "/reports/export", "/reports/export.csv"}) {
            mvc.perform(get(path).with(user("ADM001").roles("System Administrator")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void faqResultSummaryDoesNotRenderTemplateIdentifiers() throws Exception {
        Faq faq = new Faq(); faq.setQuestion("Regression published FAQ");
        faq.setAnswer("Regression answer"); faq.setStatus(FaqStatus.PUBLISHED); faqs.save(faq);
        mvc.perform(get("/faq").with(user("STU001").roles("Student")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("selectedCategory.categoryName"))));
    }

    @Test void newMutationsRequirePermissionsAndCsrf() throws Exception {
        for (String path : new String[]{"/staff/tickets/1/unassign", "/staff/tickets/1/comments/1/delete"}) {
            mvc.perform(post(path).with(user("SUP001").roles("Help Desk Support Staff")).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/student/tickets/1/delete").with(user("STU001").roles("Student")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/notifications/read-all").with(user("STU001")))
                .andExpect(status().isForbidden());
    }

    @Test void inactiveCategoryIsNotPrefilledAndServiceLinkSelectsServiceRequest() throws Exception {
        Category category = new Category();
        category.setCategoryName("Inactive regression"); category.setStatus("INACTIVE");
        category = categories.save(category);
        var result = mvc.perform(get("/student/tickets/new").param("type", "SERVICE_REQUEST")
                .param("categoryId", category.getCategoryId().toString())
                .with(user("STU001").authorities(() -> "ROLE_Student", () -> "SUBMIT_TICKET")))
                .andExpect(status().isOk()).andReturn();
        var form = (com.university.helpdesk.dto.TicketSubmissionForm) result.getModelAndView().getModel().get("form");
        assertNull(form.getCategoryId());
        assertEquals(TicketType.SERVICE_REQUEST, form.getTicketType());
    }

    @Test void loginByEmailResetsFailuresAndRecordsTimestamp() throws Exception {
        UserAccount account = accounts.findByUniversityId("STU001").orElseThrow();
        String password = UUID.randomUUID().toString();
        account.setPasswordHash(encoder.encode(password)); account.setFailedLoginAttempts(3);
        accounts.saveAndFlush(account);
        mvc.perform(post("/login").param("login", account.getEmail()).param("password", password).with(csrf()))
                .andExpect(redirectedUrl("/student/dashboard"));
        assertEquals(0, account.getFailedLoginAttempts()); assertNotNull(account.getLastLoginAt());
    }

    @Test void fiveFailuresLockAccountForFifteenMinutes() throws Exception {
        UserAccount account = accounts.findByUniversityId("STU001").orElseThrow();
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/login").param("login", "STU001").param("password", "invalid").with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }
        assertEquals(AccountStatus.LOCKED, account.getAccountStatus());
        assertEquals(5, account.getFailedLoginAttempts());
        assertTrue(account.getLockedUntil().isAfter(java.time.LocalDateTime.now().plusMinutes(14)));
    }

    @Test void resetTokensAreHashedExpiringAndSingleUse() {
        String old = resets.createResetToken("STU001", "127.0.0.1").orElseThrow();
        String current = resets.createResetToken("STU001", "127.0.0.1").orElseThrow();
        assertFalse(resets.isValidToken(old)); assertTrue(resets.isValidToken(current));
        var account = accounts.findByUniversityId("STU001").orElseThrow();
        var token = tokens.findByUserUserIdAndUsedFalse(account.getUserId()).getFirst();
        assertNotEquals(current, token.getTokenHash()); assertEquals(64, token.getTokenHash().length());
        assertTrue(token.getExpiresAt().isAfter(java.time.LocalDateTime.now().plusMinutes(29)));
        assertTrue(resets.resetPassword(current, UUID.randomUUID().toString(), "127.0.0.1"));
        assertFalse(resets.resetPassword(current, UUID.randomUUID().toString(), "127.0.0.1"));
    }
}
