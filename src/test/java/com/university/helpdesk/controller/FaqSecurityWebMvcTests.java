package com.university.helpdesk.controller;

import com.university.helpdesk.entity.Category;
import com.university.helpdesk.entity.Faq;
import com.university.helpdesk.entity.FaqStatus;
import com.university.helpdesk.repository.CategoryRepository;
import com.university.helpdesk.repository.FaqRepository;
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

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class FaqSecurityWebMvcTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private FaqRepository faqRepository;

    private MockMvc mockMvc;

    private Category activeCategory;
    private Category inactiveCategory;
    private String uniqueSuffix;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        activeCategory = new Category();
        activeCategory.setCategoryName("Campus Services " + uniqueSuffix);
        activeCategory.setStatus("ACTIVE");
        activeCategory = categoryRepository.save(activeCategory);

        inactiveCategory = new Category();
        inactiveCategory.setCategoryName("Old Services " + uniqueSuffix);
        inactiveCategory.setStatus("INACTIVE");
        inactiveCategory = categoryRepository.save(inactiveCategory);
    }

    @Test
    @DisplayName("28. Student cannot access /admin/faqs")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student", "SUBMIT_TICKET", "VIEW_OWN_TICKETS"})
    void testStudentCannotAccessAdminFaqs() throws Exception {
        mockMvc.perform(get("/admin/faqs"))
                .andExpect(forwardedUrl("/access-denied"));
    }

    @Test
    @DisplayName("29. Support staff cannot access /admin/faqs")
    @WithMockUser(username = "SUP001", authorities = {"ROLE_Help Desk Support Staff", "VIEW_ALL_TICKETS", "ASSIGN_TICKET", "UPDATE_TICKET"})
    void testSupportStaffCannotAccessAdminFaqs() throws Exception {
        mockMvc.perform(get("/admin/faqs"))
                .andExpect(forwardedUrl("/access-denied"));
    }

    @Test
    @DisplayName("30. Admin with MANAGE_FAQ can access /admin/faqs and search analytics")
    @WithMockUser(username = "ADM001", authorities = {"ROLE_System Administrator", "MANAGE_FAQ"})
    void testAdminWithManageFaqCanAccessFaqAdminAndAnalytics() throws Exception {
        mockMvc.perform(get("/admin/faqs"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-faqs"));

        mockMvc.perform(get("/admin/faqs/search-analytics"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-faq-search-analytics"));
    }

    @Test
    @DisplayName("31. User without MANAGE_FAQ receives access-denied on search analytics")
    @WithMockUser(username = "ADM002", authorities = {"ROLE_System Administrator", "VIEW_REPORTS"})
    void testUserWithoutManageFaqReceivesAccessDeniedOnAnalytics() throws Exception {
        mockMvc.perform(get("/admin/faqs/search-analytics"))
                .andExpect(forwardedUrl("/access-denied"));
    }

    @Test
    @DisplayName("32. CSRF protection remains enabled on admin mutations")
    @WithMockUser(username = "ADM001", authorities = {"ROLE_System Administrator", "MANAGE_FAQ"})
    void testCsrfProtectionOnAdminMutations() throws Exception {
        // Without CSRF -> 403 Forbidden
        mockMvc.perform(post("/admin/faqs")
                        .param("question", "Test Question without CSRF")
                        .param("answer", "Test Answer")
                        .param("status", "PUBLISHED"))
                .andExpect(status().isForbidden());

        // With CSRF -> Redirects on success
        mockMvc.perform(post("/admin/faqs")
                        .with(csrf())
                        .param("question", "Test Question with CSRF " + uniqueSuffix)
                        .param("answer", "Test Answer")
                        .param("status", "PUBLISHED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/faqs"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    @DisplayName("33. Blank question in admin creation rejected with user-friendly flash error")
    @WithMockUser(username = "ADM001", authorities = {"ROLE_System Administrator", "MANAGE_FAQ"})
    void testAdminCreateFaqValidationFailure() throws Exception {
        mockMvc.perform(post("/admin/faqs")
                        .with(csrf())
                        .param("question", "   ")
                        .param("answer", "Valid Answer")
                        .param("status", "DRAFT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/faqs"))
                .andExpect(flash().attribute("error", "Question is required."));
    }

    @Test
    @DisplayName("34. User FAQ view contains category prefill link in ticket CTA when category is active")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student"})
    void testFaqViewCategoryPrefillCta() throws Exception {
        Faq faq = new Faq();
        faq.setQuestion("How to check card balance " + uniqueSuffix);
        faq.setAnswer("Use the card reader kiosk in building A");
        faq.setCategory(activeCategory);
        faq.setStatus(FaqStatus.PUBLISHED);
        faqRepository.save(faq);

        mockMvc.perform(get("/faq").param("categoryId", activeCategory.getCategoryId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("faq"))
                .andExpect(content().string(containsString("/student/tickets/new?categoryId=" + activeCategory.getCategoryId())));
    }

    @Test
    @DisplayName("35. Inactive category filter falls back safely and does not prefill inactive category for tickets")
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student"})
    void testInactiveCategoryFilterFallback() throws Exception {
        mockMvc.perform(get("/faq").param("categoryId", inactiveCategory.getCategoryId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("faq"))
                .andExpect(model().attribute("invalidCategoryAttempted", true))
                .andExpect(model().attribute("selectedCategoryId", (Object) null))
                .andExpect(model().attribute("activeCategoryForTicket", (Object) null));
    }
}
