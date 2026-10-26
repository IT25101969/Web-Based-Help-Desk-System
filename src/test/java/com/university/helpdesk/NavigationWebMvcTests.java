package com.university.helpdesk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class NavigationWebMvcTests extends TestAccounts {
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach void setupMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void administratorMenuSurvivesEverySection() throws Exception {
        verifyMenu("ADM001", "System Administrator", new String[]{
                "/admin/dashboard", "/admin/tickets", "/admin/reports", "/admin/users", "/admin/service-setup", "/admin/assets", "/admin/support-tools",
                "/admin/faqs", "/admin/faqs/search-analytics", "/admin/monitoring", "/admin/activity-logs", "/faq",
                "/notifications", "/notifications/preferences"});
    }

    @Test void administratorUsesInventoryInsteadOfPersonalAssets() throws Exception {
        mvc.perform(get("/my-assets").with(user("ADM001").roles("System Administrator")))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/admin/assets"));
    }

    @Test void studentMenuSurvivesSharedPages() throws Exception {
        verifyMenu("STU001", "Student", new String[]{"/student/dashboard", "/student/tickets",
                "/student/tickets/new", "/faq", "/notifications", "/notifications/preferences"});
    }

    @Test void supportMenuSurvivesSharedPages() throws Exception {
        verifyMenu("SUP001", "Help Desk Support Staff", new String[]{"/staff/dashboard", "/my-assets",
                "/staff/tickets", "/faq", "/notifications", "/notifications/preferences"});
    }

    @Test void departmentSupportMenuSurvivesSharedPages() throws Exception {
        verifyMenu("DSU001", "Department Support Team Member", new String[]{"/staff/dashboard", "/my-assets",
                "/staff/tickets", "/faq", "/notifications", "/notifications/preferences"});
    }

    @Test void managerMenuSurvivesSharedPages() throws Exception {
        verifyMenu("MGR001", "Department Manager", new String[]{"/manager/dashboard", "/my-assets",
                "/manager/tickets", "/faq", "/notifications", "/notifications/preferences"});
    }

    @Test void managementMenuSurvivesSharedPages() throws Exception {
        verifyMenu("UMG001", "University Management", new String[]{"/management/dashboard", "/my-assets",
                "/management/reports", "/faq", "/notifications", "/notifications/preferences"});
    }

    private void verifyMenu(String id, String role, String[] destinations, String... extraPages) throws Exception {
        var authorities = Arrays.stream(new String[]{"ROLE_" + role, "SUBMIT_TICKET", "VIEW_OWN_TICKETS",
                "VIEW_ALL_TICKETS", "VIEW_REPORTS", "MANAGE_USERS", "MANAGE_FAQ", "MANAGE_SYSTEM"})
                .map(SimpleGrantedAuthority::new).toList();
        var pages = new java.util.ArrayList<>(List.of(destinations));
        pages.addAll(List.of(extraPages));
        for (String page : pages) {
            String html = mvc.perform(get(page).with(user(id).authorities(authorities)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var navMatcher = Pattern.compile("<nav[^>]*aria-label=\"Main navigation\"[^>]*>(.*?)</nav>", Pattern.DOTALL).matcher(html);
            assertTrue(navMatcher.find(), "Missing menu on " + page);
            String nav = navMatcher.group(1);
            assertFalse(navMatcher.find(), "Duplicate role menus on " + page);
            var links = Pattern.compile("href=\"([^\"]+)\"").matcher(nav);
            var actual = new java.util.ArrayList<String>();
            while (links.find()) actual.add(links.group(1));
            assertEquals(List.of(destinations), actual, "Menu changed on " + page);
            assertEquals(1, nav.split("aria-current=\"page\"", -1).length - 1, "Active section on " + page);
            assertTrue(html.contains("name=\"_csrf\""), "Logout CSRF missing on " + page);
        }
    }
}
