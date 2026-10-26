package com.university.helpdesk;

import com.university.helpdesk.entity.ActivityLog;
import com.university.helpdesk.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest @Transactional
class ActivityLogManagementTests extends TestAccounts {
    @Autowired ActivityLogRepository logs;
    @Autowired UserAccountRepository users;
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    String marker;
    @BeforeEach void setup() {
        marker = "AUDIT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user("ADM001").authorities(() -> "ROLE_System Administrator", () -> "MANAGE_SYSTEM");
    }
    ActivityLog event(int daysAgo) {
        ActivityLog log = new ActivityLog(); log.setUser(users.findByUniversityId("ADM001").orElseThrow());
        log.setAction(marker); log.setEntityType("TEST_RESOURCE"); log.setEntityId((long) daysAgo);
        log.setIpAddress("127.0.0.1"); log.setTimestamp(LocalDateTime.now().minusDays(daysAgo));
        return logs.save(log);
    }
    @Test void filtersAndPaginationUseDatabaseCountsAndBoundInvalidPages() throws Exception {
        for (int i = 0; i < 12; i++) event(i % 2);
        mvc.perform(get("/admin/activity-logs").with(admin()).param("action", marker).param("entityType", "TEST_RESOURCE").param("search", "ADM001").param("size", "10").param("page", "999"))
                .andExpect(status().isOk()).andExpect(model().attribute("resultCount", 12L))
                .andExpect(model().attribute("page", 1)).andExpect(model().attribute("pageCount", 2))
                .andExpect(model().attribute("logs", hasSize(2))).andExpect(content().string(containsString("Activity events")));
    }
    @Test void inclusiveDateRangeAndInvalidRangeAreHandled() throws Exception {
        event(0); event(5);
        String today = java.time.LocalDate.now().toString();
        mvc.perform(get("/admin/activity-logs").with(admin()).param("action", marker).param("start", today).param("end", today))
                .andExpect(status().isOk()).andExpect(model().attribute("resultCount", 1L));
        mvc.perform(get("/admin/activity-logs").with(admin()).param("start", today).param("end", java.time.LocalDate.now().minusDays(1).toString()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("error")).andExpect(model().attribute("logs", hasSize(0)));
    }
    @Test void csvExportHonorsFiltersAndProtectsSpreadsheetCells() throws Exception {
        ActivityLog log = event(0); log.setIpAddress("=danger"); logs.saveAndFlush(log);
        mvc.perform(get("/admin/activity-logs/export.csv").with(admin()).param("action", marker))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", "attachment; filename=\"activity-logs.csv\""))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("Timestamp,Actor ID,Actor Name,Action,Entity Type,Entity ID,IP Address")))
                .andExpect(content().string(containsString("\"'=danger\"")));
    }
    @Test void auditPageAndExportRequireSystemManagementPermission() throws Exception {
        mvc.perform(get("/admin/activity-logs").with(user("ADM001").roles("System Administrator"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/activity-logs/export.csv").with(user("STU001").roles("Student"))).andExpect(status().isForbidden());
    }
    @Test void renderOptionalVisualPreview() throws Exception {
        String previewDir = System.getProperty("ui.preview.dir"); if (previewDir == null) return;
        for (int i = 0; i < 8; i++) event(i % 3);
        var result = mvc.perform(get("/admin/activity-logs").with(admin())).andExpect(status().isOk()).andReturn();
        var directory = java.nio.file.Path.of(previewDir); java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.writeString(directory.resolve("activity-logs.html"), result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
