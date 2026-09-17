package com.university.helpdesk;

import com.university.helpdesk.controller.AdminUserController;
import com.university.helpdesk.dto.MonitoringSummary;
import com.university.helpdesk.dto.ReportSummary;
import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.AdminMonitoringService;
import com.university.helpdesk.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class ReportAndAdminServiceTests {

    @Autowired
    private ReportService reportService;

    @Autowired
    private AdminMonitoringService monitoringService;

    @Autowired
    private AdminUserController adminUserController;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketAssignmentRepository ticketAssignmentRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    private UserAccount testStudent;
    private UserAccount testAdmin;
    private UserAccount testStaff;
    private Department testDeptA;
    private Department testDeptB;
    private Category testCatA;
    private Category testCatB;

    private Role ensureRole(String roleName, String description) {
        return roleRepository.findByRoleName(roleName).orElseGet(() -> {
            Role r = new Role();
            r.setRoleName(roleName);
            r.setDescription(description);
            return roleRepository.save(r);
        });
    }

    @BeforeEach
    void setUp() {
        Role adminRole = ensureRole("System Administrator", "System Administrator");
        Role staffRole = ensureRole("Help Desk Support Staff", "Help Desk Support Staff");
        Role managerRole = ensureRole("Department Manager", "Department Manager");
        Role teamMemberRole = ensureRole("Department Support Team Member", "Department Support Team Member");
        Role studentRole = ensureRole("Student", "Student");
        Role managementRole = ensureRole("University Management", "University Management");

        testStudent = userAccountRepository.findByUniversityId("STU9901").orElseGet(() -> {
            UserAccount u = new UserAccount();
            u.setUniversityId("STU9901");
            u.setEmail("stu9901@university.edu");
            u.setFirstName("Report");
            u.setLastName("Student");
            u.setPasswordHash("$2a$10$dummyhashfortests");
            u.setAccountStatus(AccountStatus.ACTIVE);
            u = userAccountRepository.save(u);

            Student s = new Student();
            s.setUser(u);
            s.setFaculty("Computing");
            s.setProgram("IT");
            s.setAcademicYear(2);
            studentRepository.save(s);
            return u;
        });

        testAdmin = userAccountRepository.findByUniversityId("ADM9901").orElseGet(() -> {
            UserAccount u = new UserAccount();
            u.setUniversityId("ADM9901");
            u.setEmail("adm9901@university.edu");
            u.setFirstName("Report");
            u.setLastName("Admin");
            u.setPasswordHash("$2a$10$dummyhashfortests");
            u.setAccountStatus(AccountStatus.ACTIVE);
            u = userAccountRepository.save(u);

            UserRole ur = new UserRole();
            ur.setUser(u);
            ur.setRole(adminRole);
            ur.setActive(true);
            ur.setAssignedDate(LocalDateTime.now());
            userRoleRepository.save(ur);
            return u;
        });

        testStaff = userAccountRepository.findByUniversityId("STF9901").orElseGet(() -> {
            UserAccount u = new UserAccount();
            u.setUniversityId("STF9901");
            u.setEmail("stf9901@university.edu");
            u.setFirstName("Report");
            u.setLastName("Staff");
            u.setPasswordHash("$2a$10$dummyhashfortests");
            u.setAccountStatus(AccountStatus.ACTIVE);
            u = userAccountRepository.save(u);

            UserRole ur = new UserRole();
            ur.setUser(u);
            ur.setRole(staffRole);
            ur.setActive(true);
            ur.setAssignedDate(LocalDateTime.now());
            userRoleRepository.save(ur);
            return u;
        });

        testDeptA = departmentRepository.findByDepartmentName("Report Dept Alpha").orElseGet(() -> {
            Department d = new Department();
            d.setDepartmentName("Report Dept Alpha");
            d.setDescription("Test Dept A");
            return departmentRepository.save(d);
        });

        testDeptB = departmentRepository.findByDepartmentName("Report Dept Beta").orElseGet(() -> {
            Department d = new Department();
            d.setDepartmentName("Report Dept Beta");
            d.setDescription("Test Dept B");
            return departmentRepository.save(d);
        });

        testCatA = categoryRepository.findByCategoryName("Report Cat Alpha").orElseGet(() -> {
            Category c = new Category();
            c.setCategoryName("Report Cat Alpha");
            c.setDepartment(testDeptA);
            c.setStatus("ACTIVE");
            return categoryRepository.save(c);
        });

        testCatB = categoryRepository.findByCategoryName("Report Cat Beta").orElseGet(() -> {
            Category c = new Category();
            c.setCategoryName("Report Cat Beta");
            c.setDepartment(testDeptB);
            c.setStatus("ACTIVE");
            return categoryRepository.save(c);
        });
    }

    private Ticket createTicket(Category cat, TicketStatus status, LocalDateTime created, LocalDateTime resolved) {
        Ticket t = new Ticket();
        t.setReferenceNo("TICK-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        t.setStudent(studentRepository.findById(testStudent.getUserId()).orElseThrow());
        t.setCategory(cat);
        t.setTicketType(TicketType.INCIDENT);
        t.setSubject("Test Ticket " + System.nanoTime());
        t.setDescription("Test ticket description");
        t.setPriority(TicketPriority.MEDIUM);
        t.setStatus(status);
        t.setCreatedDate(created);
        t.setUpdatedDate(created);
        t.setResolvedDate(resolved);
        if (status == TicketStatus.CLOSED) {
            t.setClosedDate(resolved != null ? resolved.plusHours(1) : created.plusHours(2));
        }
        return ticketRepository.save(t);
    }

    private TicketAssignment assignTicket(Ticket ticket, UserAccount staff, LocalDateTime assignedDate, AssignmentStatus status) {
        TicketAssignment a = new TicketAssignment();
        a.setTicket(ticket);
        a.setAssignedToUser(staff);
        a.setAssignedByUser(testAdmin);
        a.setAssignedDate(assignedDate);
        a.setStatus(status);
        return ticketAssignmentRepository.save(a);
    }

    @Test
    void testReportTotalsAndStatusCountsAreDatabaseBacked() {
        LocalDateTime base = LocalDateTime.now().minusDays(3);
        createTicket(testCatA, TicketStatus.NEW, base, null);
        createTicket(testCatA, TicketStatus.ASSIGNED, base.plusHours(1), null);
        createTicket(testCatA, TicketStatus.IN_PROGRESS, base.plusHours(2), null);
        createTicket(testCatB, TicketStatus.ESCALATED, base.plusHours(3), null);
        createTicket(testCatB, TicketStatus.RESOLVED, base.plusHours(4), base.plusHours(6));
        createTicket(testCatB, TicketStatus.CLOSED, base.plusHours(5), base.plusHours(8));

        ReportSummary summary = reportService.buildSummary(LocalDate.now().minusDays(5), LocalDate.now(), null);
        assertTrue(summary.totalTickets() >= 6);
        assertTrue(summary.newTickets() >= 1);
        assertTrue(summary.assignedTickets() >= 1);
        assertTrue(summary.inProgressTickets() >= 1);
        assertTrue(summary.escalatedTickets() >= 1);
        assertTrue(summary.resolvedTickets() >= 1);
        assertTrue(summary.closedTickets() >= 1);
    }

    @Test
    void testDateRangeAndDepartmentFilteringInclusive() {
        LocalDate today = LocalDate.now();
        LocalDateTime yesterdayNoon = today.minusDays(1).atTime(12, 0);
        LocalDateTime todayNoon = today.atTime(12, 0);
        LocalDateTime tomorrowNoon = today.plusDays(1).atTime(12, 0);

        Ticket t1 = createTicket(testCatA, TicketStatus.NEW, yesterdayNoon, null);
        Ticket t2 = createTicket(testCatA, TicketStatus.RESOLVED, todayNoon, todayNoon.plusHours(1));
        Ticket t3 = createTicket(testCatB, TicketStatus.CLOSED, todayNoon, todayNoon.plusHours(2));
        Ticket t4 = createTicket(testCatA, TicketStatus.NEW, tomorrowNoon, null);

        // Filter: Department A only, Date: yesterday to today (inclusive)
        ReportSummary summary = reportService.buildSummary(today.minusDays(1), today, testDeptA.getDepartmentId());
        assertTrue(summary.totalTickets() >= 2);
        assertTrue(summary.departmentVolumes().containsKey("Report Dept Alpha"));
        assertFalse(summary.departmentVolumes().containsKey("Report Dept Beta"));

        List<Ticket> filtered = reportService.filteredTickets(today.minusDays(1), today, testDeptA.getDepartmentId());
        assertTrue(filtered.stream().anyMatch(t -> t.getTicketId().equals(t1.getTicketId())));
        assertTrue(filtered.stream().anyMatch(t -> t.getTicketId().equals(t2.getTicketId())));
        assertFalse(filtered.stream().anyMatch(t -> t.getTicketId().equals(t3.getTicketId()))); // Dept B
        assertFalse(filtered.stream().anyMatch(t -> t.getTicketId().equals(t4.getTicketId()))); // Tomorrow
    }

    @Test
    void testInvalidDateRangeReturnsEmptySummary() {
        LocalDate start = LocalDate.now();
        LocalDate end = LocalDate.now().minusDays(2);

        ReportSummary summary = reportService.buildSummary(start, end, null);
        assertEquals(0, summary.totalTickets());
        assertEquals(0.0, summary.resolutionRate());
        assertEquals(0.0, summary.averageResponseHours());
        assertEquals(0.0, summary.averageResolutionHours());
        assertTrue(summary.departmentVolumes().isEmpty());
        assertTrue(summary.activeWorkloadByStaff().isEmpty());

        List<Ticket> tickets = reportService.filteredTickets(start, end, null);
        assertTrue(tickets.isEmpty());
    }

    @Test
    void testResolutionRateCalculation() {
        LocalDateTime now = LocalDateTime.now().minusDays(2);
        Ticket t1 = createTicket(testCatA, TicketStatus.NEW, now, null);
        Ticket t2 = createTicket(testCatA, TicketStatus.IN_PROGRESS, now, null);
        Ticket t3 = createTicket(testCatA, TicketStatus.RESOLVED, now, now.plusHours(2));
        Ticket t4 = createTicket(testCatA, TicketStatus.CLOSED, now, now.plusHours(3));

        // In Dept A, we have t1 (new), t2 (in progress), t3 (resolved), t4 (closed)
        ReportSummary summary = reportService.buildSummary(LocalDate.now().minusDays(3), LocalDate.now().plusDays(1), testDeptA.getDepartmentId());
        // completed = 2 (resolved + closed), total >= 4
        assertTrue(summary.resolutionRate() > 0.0);
        assertTrue(summary.resolutionRate() <= 100.0);
    }

    @Test
    void testAverageResponseAndResolutionTimeDeterministic() {
        LocalDateTime created = LocalDateTime.now().minusDays(2);
        Ticket t = createTicket(testCatA, TicketStatus.RESOLVED, created, created.plusHours(4)); // 4 hours resolution

        // First assignment 2 hours after creation
        assignTicket(t, testStaff, created.plusHours(2), AssignmentStatus.ACTIVE);

        ReportSummary summary = reportService.buildSummary(LocalDate.now().minusDays(3), LocalDate.now().plusDays(1), testDeptA.getDepartmentId());

        assertTrue(summary.averageResponseHours() > 0.0);
        assertTrue(summary.averageResolutionHours() > 0.0);
    }

    @Test
    void testActiveWorkloadByStaff() {
        LocalDateTime now = LocalDateTime.now().minusDays(1);
        Ticket t = createTicket(testCatA, TicketStatus.IN_PROGRESS, now, null);
        assignTicket(t, testStaff, now.plusMinutes(10), AssignmentStatus.ACTIVE);

        ReportSummary summary = reportService.buildSummary(LocalDate.now().minusDays(2), LocalDate.now().plusDays(1), testDeptA.getDepartmentId());
        String staffKey = testStaff.getFirstName() + " " + testStaff.getLastName() + " (" + testStaff.getUniversityId() + ")";
        assertTrue(summary.activeWorkloadByStaff().containsKey(staffKey));
        assertTrue(summary.activeWorkloadByStaff().get(staffKey) >= 1);
    }

    @Test
    void testAdminCanCreateUserAndAuditsUserCreated() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                testAdmin.getUniversityId(), "password", List.of(new SimpleGrantedAuthority("ROLE_System Administrator"), new SimpleGrantedAuthority("MANAGE_USERS"))
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        Role studentRole = roleRepository.findByRoleName("Student").orElseThrow();

        adminUserController.create(
                "STU_NEW_99",
                "newstudent99@university.edu",
                "password123",
                "Alice",
                "Smith",
                studentRole.getRoleId(),
                auth,
                request,
                redirectAttributes
        );

        Optional<UserAccount> created = userAccountRepository.findByUniversityId("STU_NEW_99");
        assertTrue(created.isPresent());
        assertEquals("newstudent99@university.edu", created.get().getEmail());
        assertEquals(AccountStatus.ACTIVE, created.get().getAccountStatus());

        // Check activity log
        List<ActivityLog> logs = activityLogRepository.findAll();
        assertTrue(logs.stream().anyMatch(l -> "USER_CREATED".equals(l.getAction()) && l.getEntityId().equals(created.get().getUserId())));
    }

    @Test
    void testDuplicateUniversityIdAndEmailRejected() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                testAdmin.getUniversityId(), "password", List.of(new SimpleGrantedAuthority("ROLE_System Administrator"))
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        Role studentRole = roleRepository.findByRoleName("Student").orElseThrow();

        // Duplicate university ID
        adminUserController.create(
                testStudent.getUniversityId(),
                "unique_email_123@university.edu",
                "password123",
                "Bob",
                "Jones",
                studentRole.getRoleId(),
                auth,
                request,
                redirectAttributes
        );
        assertEquals("University ID already exists.", redirectAttributes.getFlashAttributes().get("error"));

        // Duplicate email
        adminUserController.create(
                "STU_UNIQUE_999",
                testStudent.getEmail(),
                "password123",
                "Bob",
                "Jones",
                studentRole.getRoleId(),
                auth,
                request,
                redirectAttributes
        );
        assertEquals("Email already exists.", redirectAttributes.getFlashAttributes().get("error"));
    }

    @Test
    void testDisableUserAndUnlockResetsFailedAttemptsAndLockedUntil() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                testAdmin.getUniversityId(), "password", List.of(new SimpleGrantedAuthority("ROLE_System Administrator"))
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // Disable user
        adminUserController.status(testStudent.getUserId(), AccountStatus.DISABLED, auth, request, redirect);
        UserAccount disabledUser = userAccountRepository.findById(testStudent.getUserId()).orElseThrow();
        assertEquals(AccountStatus.DISABLED, disabledUser.getAccountStatus());

        // Set to locked with failed attempts
        disabledUser.setAccountStatus(AccountStatus.LOCKED);
        disabledUser.setFailedLoginAttempts(5);
        disabledUser.setLockedUntil(LocalDateTime.now().plusHours(1));
        userAccountRepository.save(disabledUser);

        // Unlock user
        adminUserController.unlock(testStudent.getUserId(), auth, request, redirect);
        UserAccount unlockedUser = userAccountRepository.findById(testStudent.getUserId()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, unlockedUser.getAccountStatus());
        assertEquals(0, unlockedUser.getFailedLoginAttempts());
        assertNull(unlockedUser.getLockedUntil());

        List<ActivityLog> logs = activityLogRepository.findAll();
        assertTrue(logs.stream().anyMatch(l -> "USER_UNLOCKED".equals(l.getAction()) && l.getEntityId().equals(testStudent.getUserId())));
    }

    @Test
    void testCannotDisableOrLockOwnAdminAccount() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                testAdmin.getUniversityId(), "password", List.of(new SimpleGrantedAuthority("ROLE_System Administrator"))
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        adminUserController.status(testAdmin.getUserId(), AccountStatus.DISABLED, auth, request, redirect);
        assertEquals("Administrators cannot disable or lock their own account.", redirect.getFlashAttributes().get("error"));

        adminUserController.status(testAdmin.getUserId(), AccountStatus.LOCKED, auth, request, redirect);
        assertEquals("Administrators cannot disable or lock their own account.", redirect.getFlashAttributes().get("error"));

        UserAccount adminAfter = userAccountRepository.findById(testAdmin.getUserId()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, adminAfter.getAccountStatus());
    }

    @Test
    void testCannotRemoveOwnSystemAdministratorRole() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                testAdmin.getUniversityId(), "password", List.of(new SimpleGrantedAuthority("ROLE_System Administrator"))
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        Role adminRole = roleRepository.findByRoleName("System Administrator").orElseThrow();

        adminUserController.removeRole(testAdmin.getUserId(), adminRole.getRoleId(), auth, request, redirect);
        assertEquals("Administrators cannot remove their own System Administrator role.", redirect.getFlashAttributes().get("error"));

        UserRole ur = userRoleRepository.findByUserUserIdAndRoleRoleId(testAdmin.getUserId(), adminRole.getRoleId()).orElseThrow();
        assertTrue(ur.getActive());
    }

    @Test
    void testRoleReactivationWhenPreviouslyDeactivated() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                testAdmin.getUniversityId(), "password", List.of(new SimpleGrantedAuthority("ROLE_System Administrator"))
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        Role staffRole = roleRepository.findByRoleName("Help Desk Support Staff").orElseThrow();

        // Remove staff role
        adminUserController.removeRole(testStaff.getUserId(), staffRole.getRoleId(), auth, request, redirect);
        UserRole ur = userRoleRepository.findByUserUserIdAndRoleRoleId(testStaff.getUserId(), staffRole.getRoleId()).orElseThrow();
        assertFalse(ur.getActive());

        // Now assign role again -> should reactivate existing row, not insert duplicate or reject
        adminUserController.addRole(testStaff.getUserId(), staffRole.getRoleId(), auth, request, redirect);
        assertEquals("Role assigned.", redirect.getFlashAttributes().get("success"));

        UserRole reactivated = userRoleRepository.findByUserUserIdAndRoleRoleId(testStaff.getUserId(), staffRole.getRoleId()).orElseThrow();
        assertTrue(reactivated.getActive());
    }

    @Test
    void testAdminMonitoringSummaryFromDatabase() {
        MonitoringSummary summary = monitoringService.getMonitoringSummary();
        assertTrue(summary.totalUsers() >= 3);
        assertTrue(summary.activeUsers() >= 2);
        assertNotNull(summary.ticketStatusCounts());
        assertTrue(summary.unresolvedTickets() >= 0);
        assertNotNull(summary.recentActivityLogs());
    }

    @Test
    void testNoDataReportReturnsZeroTotalsAndEmptyMaps() {
        // Date range in the far future where no tickets exist
        LocalDate futureStart = LocalDate.now().plusYears(10);
        LocalDate futureEnd = LocalDate.now().plusYears(11);

        ReportSummary summary = reportService.buildSummary(futureStart, futureEnd, null);
        assertEquals(0, summary.totalTickets());
        assertEquals(0, summary.newTickets());
        assertEquals(0, summary.assignedTickets());
        assertEquals(0, summary.inProgressTickets());
        assertEquals(0, summary.escalatedTickets());
        assertEquals(0, summary.resolvedTickets());
        assertEquals(0, summary.closedTickets());
        assertEquals(0.0, summary.resolutionRate());
        assertEquals(0.0, summary.averageResponseHours());
        assertEquals(0.0, summary.averageResolutionHours());
        assertTrue(summary.departmentVolumes().isEmpty());
        assertTrue(summary.activeWorkloadByStaff().isEmpty());
    }

    @Test
    void testCannotDisableLastUsableAdmin() {
        // Create an isolated admin who is the ONLY admin in a separate check context
        UserAccount solitaryAdmin = new UserAccount();
        solitaryAdmin.setUniversityId("SOLITARY_ADM");
        solitaryAdmin.setEmail("solitary_adm@university.edu");
        solitaryAdmin.setFirstName("Solitary");
        solitaryAdmin.setLastName("Admin");
        solitaryAdmin.setPasswordHash("$2a$10$dummy");
        solitaryAdmin.setAccountStatus(AccountStatus.ACTIVE);
        solitaryAdmin = userAccountRepository.save(solitaryAdmin);

        Role adminRole = roleRepository.findByRoleName("System Administrator").orElseThrow();
        UserRole ur = new UserRole();
        ur.setUser(solitaryAdmin);
        ur.setRole(adminRole);
        ur.setActive(true);
        ur.setAssignedDate(LocalDateTime.now());
        userRoleRepository.save(ur);

        // Deactivate all OTHER admin roles temporarily to test solitary admin protection
        List<UserRole> otherAdmins = userRoleRepository.findByRoleRoleNameAndActiveTrue("System Administrator");
        for (UserRole o : otherAdmins) {
            if (!o.getUser().getUserId().equals(solitaryAdmin.getUserId())) {
                o.setActive(false);
                userRoleRepository.save(o);
            }
        }

        // Another user attempting to disable the solitary admin
        UserAccount callerUser1 = new UserAccount();
        callerUser1.setUniversityId("CALLER_USER_1");
        callerUser1.setEmail("caller1@university.edu");
        callerUser1.setFirstName("Caller");
        callerUser1.setLastName("User");
        callerUser1.setPasswordHash("$2a$10$dummy");
        callerUser1.setAccountStatus(AccountStatus.ACTIVE);
        callerUser1 = userAccountRepository.save(callerUser1);

        Authentication auth1 = new UsernamePasswordAuthenticationToken(
                callerUser1.getUniversityId(), "password", List.of(new SimpleGrantedAuthority("ROLE_System Administrator"))
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        adminUserController.status(solitaryAdmin.getUserId(), AccountStatus.DISABLED, auth1, request, redirect);
        assertEquals("Cannot disable or lock the last usable System Administrator account.", redirect.getFlashAttributes().get("error"));

        UserAccount stillActive = userAccountRepository.findById(solitaryAdmin.getUserId()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, stillActive.getAccountStatus());
    }

    @Test
    void testCannotRemoveRoleFromLastUsableAdmin() {
        UserAccount solitaryAdmin = new UserAccount();
        solitaryAdmin.setUniversityId("SOLITARY_ADM_2");
        solitaryAdmin.setEmail("solitary_adm2@university.edu");
        solitaryAdmin.setFirstName("Solitary2");
        solitaryAdmin.setLastName("Admin2");
        solitaryAdmin.setPasswordHash("$2a$10$dummy");
        solitaryAdmin.setAccountStatus(AccountStatus.ACTIVE);
        solitaryAdmin = userAccountRepository.save(solitaryAdmin);

        Role adminRole = roleRepository.findByRoleName("System Administrator").orElseThrow();
        UserRole ur = new UserRole();
        ur.setUser(solitaryAdmin);
        ur.setRole(adminRole);
        ur.setActive(true);
        ur.setAssignedDate(LocalDateTime.now());
        userRoleRepository.save(ur);

        // Deactivate all other admin roles
        List<UserRole> otherAdmins = userRoleRepository.findByRoleRoleNameAndActiveTrue("System Administrator");
        for (UserRole o : otherAdmins) {
            if (!o.getUser().getUserId().equals(solitaryAdmin.getUserId())) {
                o.setActive(false);
                userRoleRepository.save(o);
            }
        }

        UserAccount callerUser2 = new UserAccount();
        callerUser2.setUniversityId("CALLER_USER_2");
        callerUser2.setEmail("caller2@university.edu");
        callerUser2.setFirstName("Caller2");
        callerUser2.setLastName("User2");
        callerUser2.setPasswordHash("$2a$10$dummy");
        callerUser2.setAccountStatus(AccountStatus.ACTIVE);
        callerUser2 = userAccountRepository.save(callerUser2);

        Authentication auth2 = new UsernamePasswordAuthenticationToken(
                callerUser2.getUniversityId(), "password", List.of(new SimpleGrantedAuthority("ROLE_System Administrator"))
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        adminUserController.removeRole(solitaryAdmin.getUserId(), adminRole.getRoleId(), auth2, request, redirect);
        assertEquals("Cannot remove the role from the last usable System Administrator account.", redirect.getFlashAttributes().get("error"));

        UserRole stillAssigned = userRoleRepository.findByUserUserIdAndRoleRoleId(solitaryAdmin.getUserId(), adminRole.getRoleId()).orElseThrow();
        assertTrue(stillAssigned.getActive());
    }

    @Test
    void testSuspiciousAccountDetection() {
        UserAccount lockedUser = new UserAccount();
        lockedUser.setUniversityId("SUSP_LOCKED_1");
        lockedUser.setEmail("locked1@university.edu");
        lockedUser.setFirstName("Locked");
        lockedUser.setLastName("User");
        lockedUser.setPasswordHash("$2a$10$dummy");
        lockedUser.setAccountStatus(AccountStatus.LOCKED);
        userAccountRepository.save(lockedUser);

        UserAccount failedLoginsUser = new UserAccount();
        failedLoginsUser.setUniversityId("SUSP_FAILS_1");
        failedLoginsUser.setEmail("fails1@university.edu");
        failedLoginsUser.setFirstName("Fails");
        failedLoginsUser.setLastName("User");
        failedLoginsUser.setPasswordHash("$2a$10$dummy");
        failedLoginsUser.setAccountStatus(AccountStatus.ACTIVE);
        failedLoginsUser.setFailedLoginAttempts(4);
        userAccountRepository.save(failedLoginsUser);

        long count = userAccountRepository.countByAccountStatusOrFailedLoginAttemptsGreaterThanEqual(AccountStatus.LOCKED, 3);
        assertTrue(count >= 2);
    }
}
