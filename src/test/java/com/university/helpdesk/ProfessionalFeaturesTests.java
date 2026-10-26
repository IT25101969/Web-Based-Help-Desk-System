package com.university.helpdesk;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ProfessionalFeaturesTests extends TestAccounts {
    @Autowired TicketWorkspaceService workspace;
    @Autowired TicketService ticketService;
    @Autowired ServiceCatalogService catalog;
    @Autowired TicketDeadlineService deadlines;
    @Autowired CategoryRepository categories;
    @Autowired DepartmentRepository departments;
    @Autowired UserAccountRepository accounts;
    @Autowired UserDepartmentRepository memberships;
    @Autowired TicketAssignmentRepository assignments;
    @Autowired FaqRepository faqs;
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    String marker;
    @BeforeEach void setup() {
        marker = UUID.randomUUID().toString();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    Category category(Department department) {
        Category c = new Category(); c.setCategoryName(UUID.randomUUID().toString()); c.setDepartment(department);
        return categories.save(c);
    }
    Department department() {
        Department d = new Department(); d.setDepartmentName(UUID.randomUUID().toString()); return departments.save(d);
    }
    Ticket ticket(String student, Category category, String subject) {
        return ticketService.createTicket(student, category.getCategoryId(), TicketType.INCIDENT, subject, "Regression request", "Connectivity", "MEDIUM");
    }
    @Test void paginatedStudentSearchNeverIncludesOtherStudents() {
        Category c = category(null);
        for (int i = 0; i < 12; i++) ticket("STU001", c, marker + " " + i);
        ticket("STU002", c, marker + " private");
        var first = workspace.search("STU001", "student", marker, null, null, null, false, null, "newest", 0, 5);
        assertEquals(12, first.getTotalElements()); assertEquals(5, first.getContent().size());
        var last = workspace.search("STU001", "student", marker, null, null, null, false, null, "newest", 99, 5);
        assertEquals(2, last.getNumber()); assertEquals(2, last.getContent().size());
        assertTrue(last.getContent().stream().allMatch(t -> t.getStudent().getUser().getUniversityId().equals("STU001")));
    }
    @Test void supportSearchRespectsActiveDepartmentMembership() {
        Department permitted = department();
        UserDepartment membership = new UserDepartment(); membership.setDepartment(permitted);
        membership.setUser(accounts.findByUniversityId("SUP001").orElseThrow()); memberships.save(membership);
        Ticket visible = ticket("STU001", category(permitted), marker);
        ticket("STU001", category(department()), marker);
        ticket("STU001", category(null), marker);
        var page = workspace.search("SUP001", "support", marker, null, null, null, false, null, "newest", 0, 10);
        assertEquals(1, page.getTotalElements()); assertEquals(visible.getTicketId(), page.getContent().getFirst().getTicketId());
    }
    @Test void literalWildcardSearchDoesNotMatchEveryTicket() {
        Category c = category(null);
        Ticket literal = ticket("STU001", c, marker + " 100%_done");
        ticket("STU001", c, marker + " 100percent done");
        var page = workspace.search("STU001", "student", "100%_done", null, null, null, false, null, "subject", 0, 10);
        assertEquals(1, page.getTotalElements()); assertEquals(literal.getTicketId(), page.getContent().getFirst().getTicketId());
    }
    @Test void emptySearchResetsInvalidPageAndBoundsPageSize() {
        var page = workspace.search("STU001", "student", marker, null, null, null, false, null, "newest", 99, 9999);
        assertEquals(0, page.getNumber()); assertEquals(50, page.getSize()); assertFalse(page.hasPrevious());
    }
    @Test void assignmentFilterRunsBeforePagination() {
        Category c = category(null);
        for (int i = 0; i < 7; i++) ticket("STU001", c, marker);
        Ticket assigned = ticket("STU001", c, marker);
        TicketAssignment a = new TicketAssignment(); a.setTicket(assigned);
        a.setAssignedToUser(accounts.findByUniversityId("SUP001").orElseThrow());
        a.setAssignedByUser(accounts.findByUniversityId("ADM001").orElseThrow()); assignments.save(a);
        var page = workspace.search("ADM001", "admin", marker, null, null, null, false, "ASSIGNED", "newest", 0, 5);
        assertEquals(1, page.getTotalElements()); assertEquals(assigned.getTicketId(), page.getContent().getFirst().getTicketId());
    }
    @Test void publishedSuggestionsExcludeDraftsAndInactiveCategoryRequests() throws Exception {
        Category c = category(null);
        for (FaqStatus status : new FaqStatus[]{FaqStatus.PUBLISHED, FaqStatus.DRAFT}) {
            Faq f = new Faq(); f.setCategory(c); f.setQuestion(marker + " " + status); f.setAnswer("Helpful campus guide"); f.setStatus(status); faqs.save(f);
        }
        mvc.perform(get("/faq/suggestions").param("q", marker).with(user("STU001").roles("Student")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].question").value(marker + " PUBLISHED"));
        c.setStatus("INACTIVE"); categories.saveAndFlush(c);
        mvc.perform(get("/faq/suggestions").param("categoryId", c.getCategoryId().toString()).with(user("STU001").roles("Student")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void setupRequiresAdministratorAndSystemPermissionIncludingPost() throws Exception {
        mvc.perform(get("/admin/service-setup").with(user("ADM001").roles("System Administrator"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/service-setup/departments").param("name", marker).with(csrf())
                .with(user("SUP001").authorities(() -> "ROLE_Help Desk Support Staff", () -> "MANAGE_SYSTEM"))).andExpect(status().isForbidden());
        assertTrue(departments.findByDepartmentName(marker).isEmpty());
    }
    @Test void inactiveCategoryCannotAcceptNewTicketAndHistoricalTicketsRemain() {
        Department d = catalog.saveDepartment(null, marker, "Campus support", "team@fixtures.invalid", "Building A", "ADM001", "127.0.0.1");
        Category c = catalog.saveCategory(null, marker, "Requests", d.getDepartmentId(), "ACTIVE", "ADM001", "127.0.0.1");
        Ticket original = ticket("STU001", c, marker);
        catalog.saveCategory(c.getCategoryId(), marker, "Requests", d.getDepartmentId(), "INACTIVE", "ADM001", "127.0.0.1");
        assertThrows(IllegalArgumentException.class, () -> ticket("STU001", c, marker));
        assertEquals(original.getTicketId(), ticketService.getStudentTicket(original.getTicketId(), "STU001").getTicketId());
    }
    @Test void categoryCannotRerouteWhileAssignedStaffCouldLoseAccess() {
        Department d = department(); Category c = category(d); Ticket t = ticket("STU001", c, marker);
        TicketAssignment a = new TicketAssignment(); a.setTicket(t); a.setAssignedToUser(accounts.findByUniversityId("SUP001").orElseThrow());
        a.setAssignedByUser(accounts.findByUniversityId("ADM001").orElseThrow()); assignments.save(a);
        Department other = department();
        assertThrows(IllegalArgumentException.class, () -> catalog.saveCategory(c.getCategoryId(), c.getCategoryName(), "", other.getDepartmentId(), "ACTIVE", "ADM001", "127.0.0.1"));
        assertEquals(d.getDepartmentId(), c.getDepartment().getDepartmentId());
    }
    @Test void teamMembershipAllowsSupportQueueAccessAndRejectsStudentGrants() {
        Department d = department(); ticket("STU001", category(d), marker);
        Long supportId = accounts.findByUniversityId("SUP001").orElseThrow().getUserId();
        catalog.updateMembership(d.getDepartmentId(), supportId, true, "ADM001", "127.0.0.1");
        assertEquals(1, workspace.search("SUP001", "support", marker, null, null, null, false, null, "newest", 0, 10).getTotalElements());
        catalog.updateMembership(d.getDepartmentId(), supportId, false, "ADM001", "127.0.0.1");
        assertEquals(0, workspace.search("SUP001", "support", marker, null, null, null, false, null, "newest", 0, 10).getTotalElements());
        Long studentId = accounts.findByUniversityId("STU001").orElseThrow().getUserId();
        assertThrows(IllegalArgumentException.class, () -> catalog.updateMembership(d.getDepartmentId(), studentId, true, "ADM001", "127.0.0.1"));
    }
    @Test void cannotRemoveTeamMemberWithActiveAssignments() {
        Department d = department(); Ticket t = ticket("STU001", category(d), marker);
        var support = accounts.findByUniversityId("SUP001").orElseThrow();
        catalog.updateMembership(d.getDepartmentId(), support.getUserId(), true, "ADM001", "127.0.0.1");
        TicketAssignment a = new TicketAssignment(); a.setTicket(t); a.setAssignedToUser(support);
        a.setAssignedByUser(accounts.findByUniversityId("ADM001").orElseThrow()); assignments.save(a);
        assertThrows(IllegalArgumentException.class, () -> catalog.updateMembership(d.getDepartmentId(), support.getUserId(), false, "ADM001", "127.0.0.1"));
        assertTrue(memberships.existsByUserUserIdAndDepartmentDepartmentIdAndActiveTrue(support.getUserId(), d.getDepartmentId()));
    }
    @Test void deadlinesUsePriorityAndCompletedTicketsAreNotOverdue() {
        Ticket t = ticket("STU001", category(null), marker);
        LocalDateTime now = LocalDateTime.of(2026, 9, 17, 12, 0);
        t.setPriority(TicketPriority.CRITICAL); t.setCreatedDate(now.minusHours(5));
        assertEquals("overdue", deadlines.stateAt(t, now));
        t.setCreatedDate(now.minusHours(3)); assertEquals("soon", deadlines.stateAt(t, now));
        t.setPriority(TicketPriority.LOW); assertEquals("on-track", deadlines.stateAt(t, now));
        t.setCreatedDate(now.minusDays(30)); t.setStatus(TicketStatus.CLOSED);
        assertEquals("complete", deadlines.stateAt(t, now));
    }
}
