package com.university.helpdesk;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
public class ReportAndAdminWebMvcTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private com.university.helpdesk.service.ReportService reportService;

    private Department testDept;
    private Category testCategory;
    private UserAccount testStudentUser;

    private Role ensureRole(String roleName, String description) {
        return roleRepository.findByRoleName(roleName).orElseGet(() -> {
            Role r = new Role();
            r.setRoleName(roleName);
            r.setDescription(description);
            return roleRepository.save(r);
        });
    }

    private UserAccount ensureUser(String universityId, String email, String roleName) {
        Role role = ensureRole(roleName, roleName);
        UserAccount u = userAccountRepository.findByUniversityId(universityId).orElseGet(() -> {
            UserAccount account = new UserAccount();
            account.setUniversityId(universityId);
            account.setEmail(email);
            account.setFirstName("Test");
            account.setLastName(roleName);
            account.setPasswordHash("$2a$10$dummy");
            account.setAccountStatus(AccountStatus.ACTIVE);
            return userAccountRepository.save(account);
        });

        if (!userRoleRepository.existsByUserUserIdAndRoleRoleId(u.getUserId(), role.getRoleId())) {
            UserRole ur = new UserRole();
            ur.setUser(u);
            ur.setRole(role);
            ur.setActive(true);
            ur.setAssignedDate(LocalDateTime.now());
            userRoleRepository.save(ur);
        }
        return u;
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        ensureUser("ADM001", "adm001@university.edu", "System Administrator");
        ensureUser("UMG001", "umg001@university.edu", "University Management");
        ensureUser("STF001", "stf001@university.edu", "Help Desk Support Staff");

        testStudentUser = userAccountRepository.findByUniversityId("STU_WEB_1").orElseGet(() -> {
            UserAccount u = new UserAccount();
            u.setUniversityId("STU_WEB_1");
            u.setEmail("stu_web1@university.edu");
            u.setFirstName("Web");
            u.setLastName("Student");
            u.setPasswordHash("$2a$10$dummy");
            u.setAccountStatus(AccountStatus.ACTIVE);
            u = userAccountRepository.save(u);

            Student s = new Student();
            s.setUser(u);
            s.setFaculty("Science");
            s.setProgram("Physics");
            s.setAcademicYear(1);
            studentRepository.save(s);
            return u;
        });

        testDept = departmentRepository.findByDepartmentName("Web Test Dept").orElseGet(() -> {
            Department d = new Department();
            d.setDepartmentName("Web Test Dept");
            d.setDescription("Web Test Department");
            return departmentRepository.save(d);
        });

        testCategory = categoryRepository.findByCategoryName("Web Test Cat").orElseGet(() -> {
            Category c = new Category();
            c.setCategoryName("Web Test Cat");
            c.setDepartment(testDept);
            c.setStatus("ACTIVE");
            return categoryRepository.save(c);
        });
    }

    @Test
    @WithMockUser(username = "ADM001", authorities = {"ROLE_System Administrator", "VIEW_REPORTS", "MANAGE_SYSTEM", "MANAGE_USERS", "MANAGE_FAQ"})
    void testAdminCanAccessReports() throws Exception {
        mockMvc.perform(get("/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports"))
                .andExpect(model().attributeExists("summary"))
                .andExpect(model().attributeExists("departments"));
    }

    @Test
    @WithMockUser(username = "UMG001", authorities = {"ROLE_University Management", "VIEW_REPORTS"})
    void testManagementCanAccessReports() throws Exception {
        mockMvc.perform(get("/management/reports"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports"))
                .andExpect(model().attributeExists("summary"))
                .andExpect(model().attribute("basePath", "/management"));
    }

    @Test
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student", "SUBMIT_TICKET", "VIEW_OWN_TICKETS"})
    void testStudentCannotAccessReports() throws Exception {
        mockMvc.perform(get("/admin/reports"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/management/reports"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "STF001", authorities = {"ROLE_Help Desk Support Staff", "VIEW_ALL_TICKETS", "ASSIGN_TICKET", "UPDATE_TICKET"})
    void testStaffCannotAccessReports() throws Exception {
        mockMvc.perform(get("/admin/reports"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ADM001", authorities = {"ROLE_System Administrator"})
    void testAdminWithoutViewReportsPermissionDenied() throws Exception {
        mockMvc.perform(get("/admin/reports"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "UMG001", authorities = {"ROLE_University Management", "VIEW_REPORTS"})
    void testManagementCannotAccessAdminUsers() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "STU001", authorities = {"ROLE_Student", "SUBMIT_TICKET", "VIEW_OWN_TICKETS"})
    void testStudentCannotAccessAdminUsers() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ADM001", authorities = {"ROLE_System Administrator", "MANAGE_SYSTEM"})
    void testAdminCanAccessMonitoringAndActivityLogs() throws Exception {
        mockMvc.perform(get("/admin/monitoring"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-monitoring"))
                .andExpect(model().attributeExists("summary"));

        mockMvc.perform(get("/admin/activity-logs"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-activity-logs"))
                .andExpect(model().attributeExists("logs"));
    }

    @Test
    @WithMockUser(username = "UMG001", authorities = {"ROLE_University Management", "VIEW_REPORTS"})
    void testManagementCannotAccessMonitoringOrActivityLogs() throws Exception {
        mockMvc.perform(get("/admin/monitoring"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/activity-logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ADM001", authorities = {"ROLE_System Administrator", "VIEW_REPORTS"})
    void testCsvExportValidHeadersAndFormulaProtection() throws Exception {
        // Create ticket with formula injection in subject
        Ticket t = new Ticket();
        t.setReferenceNo("TICK-FORMULA-1");
        t.setStudent(studentRepository.findById(testStudentUser.getUserId()).orElseThrow());
        t.setCategory(testCategory);
        t.setTicketType(TicketType.INCIDENT);
        t.setSubject("=1+1 Formula Injection Test");
        t.setDescription("Formula test description");
        t.setPriority(TicketPriority.HIGH);
        t.setStatus(TicketStatus.NEW);
        t.setCreatedDate(LocalDateTime.now().minusHours(1));
        t.setUpdatedDate(LocalDateTime.now().minusHours(1));
        ticketRepository.save(t);

        mockMvc.perform(get("/admin/reports/export.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"helpdesk-report.csv\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reference,Subject,Category,Department,Priority,Status,Created Date,Resolved Date,Assigned Staff")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"'=1+1 Formula Injection Test\""))); // Prefixed with apostrophe
    }

    @Test
    @WithMockUser(username = "ADM001", authorities = {"ROLE_System Administrator", "VIEW_REPORTS"})
    void testCsvExportInvalidFilterReturns400BadRequest() throws Exception {
        // Start after end
        mockMvc.perform(get("/admin/reports/export.csv")
                        .param("start", "2026-09-20")
                        .param("end", "2026-09-10"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Start date cannot be after end date.")));

        // Non-existent department
        mockMvc.perform(get("/admin/reports/export.csv")
                        .param("departmentId", "99999999"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Selected department was not found.")));
    }

    @Test
    @WithMockUser(username = "ADM001", authorities = {"ROLE_System Administrator", "VIEW_REPORTS"})
    void testReportViewedAndExportedAuditLogging() throws Exception {
        // View reports
        mockMvc.perform(get("/admin/reports"))
                .andExpect(status().isOk());

        // Export CSV
        mockMvc.perform(get("/admin/reports/export.csv"))
                .andExpect(status().isOk());

        List<ActivityLog> logs = activityLogRepository.findAll();
        assertTrue(logs.stream().anyMatch(l -> "REPORT_VIEWED".equals(l.getAction())));
        assertTrue(logs.stream().anyMatch(l -> "REPORT_EXPORTED".equals(l.getAction())));
    }

    @Test
    @WithMockUser(username = "ADM001", authorities = {"ROLE_System Administrator", "VIEW_REPORTS"})
    void testCsvEscapingWithQuotesAndCommas() throws Exception {
        Ticket t = new Ticket();
        t.setReferenceNo("TICK-QUOTE-1");
        t.setStudent(studentRepository.findById(testStudentUser.getUserId()).orElseThrow());
        t.setCategory(testCategory);
        t.setTicketType(TicketType.INCIDENT);
        t.setSubject("Need \"urgent\", priority assistance");
        t.setDescription("Quote test");
        t.setPriority(TicketPriority.HIGH);
        t.setStatus(TicketStatus.NEW);
        t.setCreatedDate(LocalDateTime.now().minusHours(1));
        t.setUpdatedDate(LocalDateTime.now().minusHours(1));
        ticketRepository.save(t);

        mockMvc.perform(get("/admin/reports/export.csv"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"Need \"\"urgent\"\", priority assistance\"")));
    }

    @Test
    @WithMockUser(username = "ADM001", authorities = {"ROLE_System Administrator", "VIEW_REPORTS"})
    void testCsvFormulaLeadingWhitespaceProtection() throws Exception {
        Ticket t = new Ticket();
        t.setReferenceNo("TICK-FORMULA-SPACE");
        t.setStudent(studentRepository.findById(testStudentUser.getUserId()).orElseThrow());
        t.setCategory(testCategory);
        t.setTicketType(TicketType.INCIDENT);
        t.setSubject("   =2+2 Formula With Spaces");
        t.setDescription("Whitespace formula test");
        t.setPriority(TicketPriority.LOW);
        t.setStatus(TicketStatus.NEW);
        t.setCreatedDate(LocalDateTime.now().minusHours(1));
        t.setUpdatedDate(LocalDateTime.now().minusHours(1));
        ticketRepository.save(t);

        mockMvc.perform(get("/admin/reports/export.csv"))
                .andExpect(status().isOk())
                // Leading spaces are preserved, apostrophe is prefixed to original value
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"'   =2+2 Formula With Spaces\"")));
    }

    @Test
    @WithMockUser(username = "ADM001", authorities = {"ROLE_System Administrator", "VIEW_REPORTS"})
    void testCsvExportFilterParityWithReportService() throws Exception {
        String response = mockMvc.perform(get("/admin/reports/export.csv")
                        .param("departmentId", testDept.getDepartmentId().toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long csvDataRows = response.lines().filter(line -> !line.isBlank()).count() - 1; // Subtract 1 for header
        List<Ticket> serviceTickets = reportService.filteredTickets(null, null, testDept.getDepartmentId());
        assertEquals(serviceTickets.size(), csvDataRows);
    }

    @Test
    @WithMockUser(username = "UMG001", authorities = {"ROLE_University Management", "VIEW_REPORTS"})
    void testManagementCannotPerformAdminMutations() throws Exception {
        mockMvc.perform(post("/admin/users")
                        .with(csrf())
                        .param("universityId", "HACK_ADM")
                        .param("email", "hack@university.edu")
                        .param("password", "password123")
                        .param("firstName", "Hacker")
                        .param("lastName", "User")
                        .param("roleId", "1"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/users/" + testStudentUser.getUserId() + "/status")
                        .with(csrf())
                        .param("status", "DISABLED"))
                .andExpect(status().isForbidden());
    }
}
