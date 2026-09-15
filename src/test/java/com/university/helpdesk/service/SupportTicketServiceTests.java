package com.university.helpdesk.service;

import com.university.helpdesk.dto.StaffWorkload;
import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class SupportTicketServiceTests {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketAssignmentService assignmentService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketAssignmentRepository assignmentRepository;

    @Autowired
    private TicketStatusHistoryRepository historyRepository;

    @Autowired
    private UserCommentRepository commentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private UserDepartmentRepository userDepartmentRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Department deptIT;
    private Department deptFinance;
    private Category catIT;
    private Category catFinance;
    private Category catUnmapped;
    private UserAccount studentUser;
    private Student studentProfile;
    private UserAccount staffIT1;
    private UserAccount staffIT2;
    private UserAccount staffFinance;
    private UserAccount managerIT;
    private UserAccount adminUser;
    private UserAccount inactiveStaff;
    private UserAccount nonSupportUser;

    private Role roleAdmin;
    private Role roleHelpDesk;
    private Role roleDeptSupport;
    private Role roleDeptManager;
    private Role roleStudent;
    private Role roleManagement;

    @BeforeEach
    void setUp() {
        String uidSuffix = UUID.randomUUID().toString().substring(0, 8);

        // Ensure roles exist
        roleAdmin = getOrCreateRole("System Administrator");
        roleHelpDesk = getOrCreateRole("Help Desk Support Staff");
        roleDeptSupport = getOrCreateRole("Department Support Team Member");
        roleDeptManager = getOrCreateRole("Department Manager");
        roleStudent = getOrCreateRole("Student");
        roleManagement = getOrCreateRole("University Management");

        // Create departments
        deptIT = new Department();
        deptIT.setDepartmentName("IT Services " + uidSuffix);
        deptIT.setContactEmail("it_" + uidSuffix + "@univ.edu");
        deptIT = departmentRepository.save(deptIT);

        deptFinance = new Department();
        deptFinance.setDepartmentName("Finance " + uidSuffix);
        deptFinance.setContactEmail("fin_" + uidSuffix + "@univ.edu");
        deptFinance = departmentRepository.save(deptFinance);

        // Create categories
        catIT = new Category();
        catIT.setCategoryName("Network Issue " + uidSuffix);
        catIT.setDepartment(deptIT);
        catIT.setStatus("ACTIVE");
        catIT = categoryRepository.save(catIT);

        catFinance = new Category();
        catFinance.setCategoryName("Fee Inquiries " + uidSuffix);
        catFinance.setDepartment(deptFinance);
        catFinance.setStatus("ACTIVE");
        catFinance = categoryRepository.save(catFinance);

        catUnmapped = new Category();
        catUnmapped.setCategoryName("Unmapped Inquiries " + uidSuffix);
        catUnmapped.setDepartment(null); // Unmapped category for manual routing tests
        catUnmapped.setStatus("ACTIVE");
        catUnmapped = categoryRepository.save(catUnmapped);

        // Create Users
        studentUser = createUser("STU_" + uidSuffix, "student_" + uidSuffix + "@univ.edu", AccountStatus.ACTIVE);
        assignRole(studentUser, roleStudent);
        studentProfile = new Student();
        studentProfile.setUser(studentUser);
        studentProfile.setFaculty("Computing");
        studentProfile.setProgram("IT");
        studentProfile.setAcademicYear(2);
        studentProfile = studentRepository.save(studentProfile);

        staffIT1 = createUser("SUP1_" + uidSuffix, "sup1_" + uidSuffix + "@univ.edu", AccountStatus.ACTIVE);
        assignRole(staffIT1, roleHelpDesk);
        assignDepartment(staffIT1, deptIT, "SUPPORT");

        staffIT2 = createUser("SUP2_" + uidSuffix, "sup2_" + uidSuffix + "@univ.edu", AccountStatus.ACTIVE);
        assignRole(staffIT2, roleHelpDesk);
        assignDepartment(staffIT2, deptIT, "SUPPORT");

        staffFinance = createUser("FIN1_" + uidSuffix, "fin1_" + uidSuffix + "@univ.edu", AccountStatus.ACTIVE);
        assignRole(staffFinance, roleDeptSupport);
        assignDepartment(staffFinance, deptFinance, "SUPPORT");

        managerIT = createUser("MGR_" + uidSuffix, "mgr_" + uidSuffix + "@univ.edu", AccountStatus.ACTIVE);
        assignRole(managerIT, roleDeptManager);
        assignDepartment(managerIT, deptIT, "MANAGER");

        adminUser = createUser("ADM_" + uidSuffix, "adm_" + uidSuffix + "@univ.edu", AccountStatus.ACTIVE);
        assignRole(adminUser, roleAdmin);

        inactiveStaff = createUser("INACT_" + uidSuffix, "inact_" + uidSuffix + "@univ.edu", AccountStatus.DISABLED);
        assignRole(inactiveStaff, roleHelpDesk);
        assignDepartment(inactiveStaff, deptIT, "SUPPORT");

        nonSupportUser = createUser("UMG_" + uidSuffix, "umg_" + uidSuffix + "@univ.edu", AccountStatus.ACTIVE);
        assignRole(nonSupportUser, roleManagement);
    }

    private Role getOrCreateRole(String roleName) {
        return roleRepository.findByRoleName(roleName)
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setRoleName(roleName);
                    r.setDescription(roleName);
                    return roleRepository.save(r);
                });
    }

    private UserAccount createUser(String universityId, String email, AccountStatus status) {
        UserAccount u = new UserAccount();
        u.setUniversityId(universityId);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("Pass@123"));
        u.setFirstName("Test");
        u.setLastName(universityId);
        u.setAccountStatus(status);
        return userAccountRepository.save(u);
    }

    private void assignRole(UserAccount user, Role role) {
        UserRole ur = new UserRole();
        ur.setUser(user);
        ur.setRole(role);
        ur.setActive(true);
        userRoleRepository.save(ur);
    }

    private void assignDepartment(UserAccount user, Department department, String type) {
        UserDepartment ud = new UserDepartment();
        ud.setUser(user);
        ud.setDepartment(department);
        ud.setMembershipType(type);
        ud.setActive(true);
        userDepartmentRepository.save(ud);
    }

    private Ticket createTicket(Category category, String subject) {
        Ticket t = new Ticket();
        t.setReferenceNo("HD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        t.setStudent(studentProfile);
        t.setCategory(category);
        t.setTicketType(TicketType.INCIDENT);
        t.setSubject(subject);
        t.setDescription("Issue description for " + subject);
        t.setPriority(TicketPriority.MEDIUM);
        t.setStatus(TicketStatus.NEW);
        return ticketRepository.save(t);
    }

    // 1. Staff sees tickets for own department
    @Test
    void testStaffSeesTicketsForOwnDepartment() {
        Ticket t1 = createTicket(catIT, "IT Ticket 1");
        createTicket(catFinance, "Finance Ticket 1");

        List<Ticket> itTickets = ticketService.getSupportTickets(staffIT1.getUniversityId());
        assertTrue(itTickets.stream().anyMatch(t -> t.getTicketId().equals(t1.getTicketId())));
        assertFalse(itTickets.stream().anyMatch(t -> t.getCategory().getDepartment().getDepartmentId().equals(deptFinance.getDepartmentId())));
    }

    // 2. Staff cannot see ticket from unauthorized department
    @Test
    void testStaffCannotAccessTicketFromUnauthorizedDepartment() {
        Ticket finTicket = createTicket(catFinance, "Finance Only Ticket");
        assertThrows(AccessDeniedException.class, () ->
                ticketService.getAuthorizedSupportTicket(finTicket.getTicketId(), staffIT1.getUniversityId())
        );
    }

    // 3. Manager sees only authorized department tickets
    @Test
    void testManagerSeesOnlyAuthorizedDepartmentTickets() {
        Ticket tIT = createTicket(catIT, "IT Ticket");
        Ticket tFin = createTicket(catFinance, "Finance Ticket");

        List<Ticket> mgrTickets = ticketService.getSupportTickets(managerIT.getUniversityId());
        assertTrue(mgrTickets.stream().anyMatch(t -> t.getTicketId().equals(tIT.getTicketId())));
        assertFalse(mgrTickets.stream().anyMatch(t -> t.getTicketId().equals(tFin.getTicketId())));

        assertThrows(AccessDeniedException.class, () ->
                ticketService.getAuthorizedSupportTicket(tFin.getTicketId(), managerIT.getUniversityId())
        );
    }

    // 4. Admin can inspect all tickets (including unmapped)
    @Test
    void testAdminCanInspectAllTicketsAndUnmapped() {
        Ticket tIT = createTicket(catIT, "IT Ticket");
        Ticket tUnmapped = createTicket(catUnmapped, "Unmapped Ticket");

        assertNotNull(ticketService.getAuthorizedSupportTicket(tIT.getTicketId(), adminUser.getUniversityId()));
        assertNotNull(ticketService.getAuthorizedSupportTicket(tUnmapped.getTicketId(), adminUser.getUniversityId()));

        List<Ticket> unmappedList = ticketService.getAdminTicketsWithFilters(null, true, null, null);
        assertTrue(unmappedList.stream().anyMatch(t -> t.getTicketId().equals(tUnmapped.getTicketId())));
    }

    // 5. Assignment to valid staff succeeds
    @Test
    void testAssignmentToValidStaffSucceeds() {
        Ticket ticket = createTicket(catIT, "Printer Issue");
        TicketAssignment assignment = assignmentService.assign(
                ticket.getTicketId(),
                staffIT1.getUserId(),
                managerIT.getUniversityId(),
                TicketPriority.HIGH
        );

        assertNotNull(assignment);
        assertEquals(AssignmentStatus.ACTIVE, assignment.getStatus());
        assertEquals(staffIT1.getUserId(), assignment.getAssignedToUser().getUserId());

        Ticket reloaded = ticketRepository.findById(ticket.getTicketId()).orElseThrow();
        assertEquals(TicketStatus.ASSIGNED, reloaded.getStatus());
        assertEquals(TicketPriority.HIGH, reloaded.getPriority());
    }

    // 6. Inactive staff assignment rejected
    @Test
    void testAssignmentToInactiveStaffRejected() {
        Ticket ticket = createTicket(catIT, "Email Issue");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                assignmentService.assign(ticket.getTicketId(), inactiveStaff.getUserId(), managerIT.getUniversityId(), null)
        );
        assertTrue(ex.getMessage().contains("Inactive or disabled staff"));
    }

    // 7. Wrong department staff assignment rejected
    @Test
    void testAssignmentToWrongDepartmentStaffRejected() {
        Ticket ticket = createTicket(catIT, "VPN Issue");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                assignmentService.assign(ticket.getTicketId(), staffFinance.getUserId(), managerIT.getUniversityId(), null)
        );
        assertTrue(ex.getMessage().contains("does not belong to the ticket department"));
    }

    // 8. Non-support user assignment rejected
    @Test
    void testAssignmentToNonSupportUserRejected() {
        Ticket ticket = createTicket(catIT, "Auth Issue");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                assignmentService.assign(ticket.getTicketId(), nonSupportUser.getUserId(), managerIT.getUniversityId(), null)
        );
        assertTrue(ex.getMessage().contains("does not have a support role"));
    }

    // 9. Unauthorized assigning actor rejected
    @Test
    void testUnauthorizedAssigningActorRejected() {
        Ticket ticket = createTicket(catIT, "Network Glitch");
        assertThrows(AccessDeniedException.class, () ->
                assignmentService.assign(ticket.getTicketId(), staffIT1.getUserId(), staffFinance.getUniversityId(), null)
        );
    }

    // 10. Reassignment lifecycle: old becomes REASSIGNED, new ACTIVE, only one ACTIVE remains
    @Test
    void testReassignmentLifecycleAndSingleActiveAssignment() {
        Ticket ticket = createTicket(catIT, "Slow Laptop");

        TicketAssignment firstAssign = assignmentService.assign(
                ticket.getTicketId(),
                staffIT1.getUserId(),
                managerIT.getUniversityId(),
                TicketPriority.MEDIUM
        );
        assertEquals(AssignmentStatus.ACTIVE, firstAssign.getStatus());

        // Reassign to staffIT2
        TicketAssignment secondAssign = assignmentService.assign(
                ticket.getTicketId(),
                staffIT2.getUserId(),
                managerIT.getUniversityId(),
                TicketPriority.HIGH
        );
        assertEquals(AssignmentStatus.ACTIVE, secondAssign.getStatus());

        TicketAssignment reloadedFirst = assignmentRepository.findById(firstAssign.getAssignmentId()).orElseThrow();
        assertEquals(AssignmentStatus.REASSIGNED, reloadedFirst.getStatus());
        assertNotNull(reloadedFirst.getEndDate());

        List<TicketAssignment> activeAssignments = assignmentRepository.findByTicketTicketIdAndStatus(ticket.getTicketId(), AssignmentStatus.ACTIVE);
        assertEquals(1, activeAssignments.size());
        assertEquals(staffIT2.getUserId(), activeAssignments.get(0).getAssignedToUser().getUserId());

        List<TicketAssignment> history = assignmentService.getHistory(ticket.getTicketId());
        assertEquals(2, history.size());
    }

    // 11. Workload calculation and overload warning at threshold (8)
    @Test
    void testWorkloadCalculationAndOverloadWarning() {
        for (int i = 0; i < 8; i++) {
            Ticket t = createTicket(catIT, "Bulk Ticket " + i);
            assignmentService.assign(t.getTicketId(), staffIT1.getUserId(), managerIT.getUniversityId(), null);
        }

        List<StaffWorkload> candidates = assignmentService.getCandidates(createTicket(catIT, "Extra Ticket").getTicketId());
        StaffWorkload staff1Workload = candidates.stream()
                .filter(c -> c.userId().equals(staffIT1.getUserId()))
                .findFirst()
                .orElseThrow();

        assertEquals(8, staff1Workload.activeAssignments());
        assertTrue(staff1Workload.overloaded());
    }

    // 12. Notifications created on assignment and reassignment
    @Test
    void testNotificationsOnAssignmentAndReassignment() {
        Ticket ticket = createTicket(catIT, "Monitor Issue");

        assignmentService.assign(ticket.getTicketId(), staffIT1.getUserId(), managerIT.getUniversityId(), null);

        List<Notification> staffNotifs = notificationRepository.findByUserUserIdOrderByCreatedDateDesc(staffIT1.getUserId());
        assertTrue(staffNotifs.stream().anyMatch(n -> n.getNotificationType() == NotificationType.ASSIGNED));

        List<Notification> studentNotifs = notificationRepository.findByUserUserIdOrderByCreatedDateDesc(studentUser.getUserId());
        assertTrue(studentNotifs.stream().anyMatch(n -> n.getNotificationType() == NotificationType.ASSIGNED));

        // Reassign to staffIT2
        assignmentService.assign(ticket.getTicketId(), staffIT2.getUserId(), managerIT.getUniversityId(), null);

        List<Notification> previousAssigneeNotifs = notificationRepository.findByUserUserIdOrderByCreatedDateDesc(staffIT1.getUserId());
        assertTrue(previousAssigneeNotifs.stream().anyMatch(n -> n.getMessage().contains("previously assigned to you was reassigned")));
    }

    // 13. Priority update succeeds for authorized user and records ActivityLog (no fake status history)
    @Test
    void testPriorityUpdateAndActivityLogging() {
        Ticket ticket = createTicket(catIT, "Server Alert");
        int historyCountBefore = historyRepository.findByTicketTicketIdOrderByChangedDateAsc(ticket.getTicketId()).size();

        Ticket updated = ticketService.updatePriority(ticket.getTicketId(), TicketPriority.CRITICAL, managerIT.getUniversityId());
        assertEquals(TicketPriority.CRITICAL, updated.getPriority());

        int historyCountAfter = historyRepository.findByTicketTicketIdOrderByChangedDateAsc(ticket.getTicketId()).size();
        assertEquals(historyCountBefore, historyCountAfter, "Priority change must not create invalid status history rows.");

        assertTrue(activityLogRepository.existsByUserUserId(managerIT.getUserId()));
    }

    // 14. Unauthorized priority update rejected
    @Test
    void testUnauthorizedPriorityUpdateRejected() {
        Ticket ticket = createTicket(catIT, "Server Alert 2");
        assertThrows(AccessDeniedException.class, () ->
                ticketService.updatePriority(ticket.getTicketId(), TicketPriority.CRITICAL, staffFinance.getUniversityId())
        );
    }

    // 15. Status workflow: valid transition succeeds, invalid transition rejected
    @Test
    void testStatusTransitions() {
        Ticket ticket = createTicket(catIT, "Account Lockout");
        final Long tid = ticket.getTicketId();
        assertEquals(TicketStatus.NEW, ticket.getStatus());

        // Invalid: NEW -> RESOLVED directly
        assertThrows(IllegalArgumentException.class, () ->
                ticketService.changeStatus(tid, TicketStatus.RESOLVED, staffIT1.getUniversityId(), "Instant resolve")
        );

        // Valid: NEW -> IN_PROGRESS
        ticket = ticketService.changeStatus(tid, TicketStatus.IN_PROGRESS, staffIT1.getUniversityId(), "Started work");
        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());

        // Valid: IN_PROGRESS -> RESOLVED
        ticket = ticketService.changeStatus(tid, TicketStatus.RESOLVED, staffIT1.getUniversityId(), "Fixed issue");
        assertEquals(TicketStatus.RESOLVED, ticket.getStatus());
        assertNotNull(ticket.getResolvedDate());

        // Correction 2: RESOLVED -> IN_PROGRESS (Reopening) clears resolvedDate
        ticket = ticketService.changeStatus(tid, TicketStatus.IN_PROGRESS, staffIT1.getUniversityId(), "Reopening ticket");
        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());
        assertNull(ticket.getResolvedDate(), "Reopening ticket must clear resolvedDate to avoid stale metric corruption.");

        // Resolve again
        ticket = ticketService.changeStatus(tid, TicketStatus.RESOLVED, staffIT1.getUniversityId(), "Resolved again");
        assertNotNull(ticket.getResolvedDate());

        // Close ticket
        ticket = ticketService.changeStatus(tid, TicketStatus.CLOSED, staffIT1.getUniversityId(), "Closing ticket");
        assertEquals(TicketStatus.CLOSED, ticket.getStatus());
        assertNotNull(ticket.getClosedDate());

        // Invalid: CLOSED -> any transition
        assertThrows(IllegalArgumentException.class, () ->
                ticketService.changeStatus(tid, TicketStatus.IN_PROGRESS, staffIT1.getUniversityId(), "Reopen closed")
        );
    }

    // 16. Terminal state assignment finalization at CLOSED
    @Test
    void testTerminalStateAssignmentFinalizationAtClosed() {
        Ticket ticket = createTicket(catIT, "Hardware Failure");
        assignmentService.assign(ticket.getTicketId(), staffIT1.getUserId(), managerIT.getUniversityId(), null);
        ticketService.changeStatus(ticket.getTicketId(), TicketStatus.IN_PROGRESS, staffIT1.getUniversityId(), "Working");
        ticketService.changeStatus(ticket.getTicketId(), TicketStatus.RESOLVED, staffIT1.getUniversityId(), "Fixed");

        // When closed
        ticketService.changeStatus(ticket.getTicketId(), TicketStatus.CLOSED, staffIT1.getUniversityId(), "Closed by support");

        List<TicketAssignment> active = assignmentRepository.findByTicketTicketIdAndStatus(ticket.getTicketId(), AssignmentStatus.ACTIVE);
        assertTrue(active.isEmpty(), "No ACTIVE assignment rows must remain when ticket is CLOSED.");

        List<TicketAssignment> completed = assignmentRepository.findByTicketTicketIdOrderByAssignedDateDesc(ticket.getTicketId());
        assertEquals(1, completed.size());
        assertEquals(AssignmentStatus.COMPLETED, completed.get(0).getStatus());
        assertNotNull(completed.get(0).getEndDate());
    }

    // 17. Escalation notification created for student and department manager
    @Test
    void testEscalationNotificationCreated() {
        Ticket ticket = createTicket(catIT, "Critical Outage");
        ticketService.changeStatus(ticket.getTicketId(), TicketStatus.ESCALATED, staffIT1.getUniversityId(), "Escalating to manager");

        List<Notification> mgrNotifs = notificationRepository.findByUserUserIdOrderByCreatedDateDesc(managerIT.getUserId());
        assertTrue(mgrNotifs.stream().anyMatch(n -> n.getNotificationType() == NotificationType.ESCALATED));

        List<Notification> stuNotifs = notificationRepository.findByUserUserIdOrderByCreatedDateDesc(studentUser.getUserId());
        assertTrue(stuNotifs.stream().anyMatch(n -> n.getNotificationType() == NotificationType.ESCALATED));
    }

    // 18. Comments: Public visible and notifies student; Internal hidden and does NOT notify student
    @Test
    void testCommentsPublicVsInternal() {
        Ticket ticket = createTicket(catIT, "Login Problem");

        // Public comment by staff
        commentService.addComment(ticket.getTicketId(), staffIT1.getUniversityId(), "Public reply to student", CommentType.PUBLIC);
        List<Notification> stuNotifs1 = notificationRepository.findByUserUserIdOrderByCreatedDateDesc(studentUser.getUserId());
        assertTrue(stuNotifs1.stream().anyMatch(n -> n.getMessage().contains("new reply was added")));

        int notifCountBefore = notificationRepository.findByUserUserIdOrderByCreatedDateDesc(studentUser.getUserId()).size();

        // Internal comment by staff
        commentService.addComment(ticket.getTicketId(), staffIT1.getUniversityId(), "Internal investigation notes", CommentType.INTERNAL);
        int notifCountAfter = notificationRepository.findByUserUserIdOrderByCreatedDateDesc(studentUser.getUserId()).size();
        assertEquals(notifCountBefore, notifCountAfter, "INTERNAL comment must NEVER generate notifications for students.");

        List<UserComment> publicComments = commentService.getPublicComments(ticket.getTicketId());
        assertEquals(1, publicComments.size());
        assertEquals("Public reply to student", publicComments.get(0).getCommentText());

        List<UserComment> allComments = commentService.getComments(ticket.getTicketId());
        assertEquals(2, allComments.size());
    }

    // 19. Blank comment rejected and unauthorized comment rejected
    @Test
    void testCommentValidationAndAuthorization() {
        Ticket ticket = createTicket(catIT, "Network Drop");

        assertThrows(IllegalArgumentException.class, () ->
                commentService.addComment(ticket.getTicketId(), staffIT1.getUniversityId(), "   ", CommentType.PUBLIC)
        );

        assertThrows(AccessDeniedException.class, () ->
                commentService.addComment(ticket.getTicketId(), staffFinance.getUniversityId(), "Attempted comment", CommentType.PUBLIC)
        );
    }

    // 20. Manual routing: unmapped ticket handled safely without crash, ordinary staff cannot assign, assignment blocked until routed
    @Test
    void testManualRoutingSafeguards() {
        Ticket unmappedTicket = createTicket(catUnmapped, "Unrouted Request");

        // Normal staff cannot access unmapped ticket
        assertThrows(AccessDeniedException.class, () ->
                ticketService.getAuthorizedSupportTicket(unmappedTicket.getTicketId(), staffIT1.getUniversityId())
        );

        // System Administrator can inspect unmapped ticket
        Ticket inspected = ticketService.getAuthorizedSupportTicket(unmappedTicket.getTicketId(), adminUser.getUniversityId());
        assertNotNull(inspected);

        // Candidates list returns empty safely without NPE
        List<StaffWorkload> candidates = assignmentService.getCandidates(unmappedTicket.getTicketId(), adminUser.getUniversityId());
        assertTrue(candidates.isEmpty());

        // Correction 1: Assignment must be blocked until a valid department is routed
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                assignmentService.assign(unmappedTicket.getTicketId(), staffIT1.getUserId(), adminUser.getUniversityId(), null)
        );
        assertTrue(ex.getMessage().contains("Ticket requires manual department routing before assignment."));
    }
}
