package com.university.helpdesk.controller;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
public class SupportTicketSecurityWebMvcTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

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
    private CommentService commentService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Department itDept;
    private Department finDept;
    private Category itCat;
    private Category finCat;
    private Category unmappedCat;

    private UserAccount stuAccount;
    private Student stuProfile;

    private UserAccount itStaffAccount;
    private UserAccount deptSupportAccount;
    private UserAccount adminAccount;

    private Ticket itTicket;
    private Ticket finTicket;
    private Ticket unmappedTicket;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Role roleAdmin = getOrCreateRole("System Administrator");
        Role roleHelpDesk = getOrCreateRole("Help Desk Support Staff");
        Role roleDeptSupport = getOrCreateRole("Department Support Team Member");
        Role roleStudent = getOrCreateRole("Student");

        itDept = new Department();
        itDept.setDepartmentName("IT Dept " + suffix);
        itDept = departmentRepository.save(itDept);

        finDept = new Department();
        finDept.setDepartmentName("Finance Dept " + suffix);
        finDept = departmentRepository.save(finDept);

        itCat = new Category();
        itCat.setCategoryName("Network " + suffix);
        itCat.setDepartment(itDept);
        itCat = categoryRepository.save(itCat);

        finCat = new Category();
        finCat.setCategoryName("Fees " + suffix);
        finCat.setDepartment(finDept);
        finCat = categoryRepository.save(finCat);

        unmappedCat = new Category();
        unmappedCat.setCategoryName("General " + suffix);
        unmappedCat.setDepartment(null);
        unmappedCat = categoryRepository.save(unmappedCat);

        stuAccount = createUser("STU_" + suffix, "stu_" + suffix + "@univ.edu");
        assignRole(stuAccount, roleStudent);
        stuProfile = new Student();
        stuProfile.setUser(stuAccount);
        stuProfile.setFaculty("Science");
        stuProfile.setProgram("Computer Science");
        stuProfile.setAcademicYear(1);
        stuProfile = studentRepository.save(stuProfile);

        itStaffAccount = createUser("SUP_" + suffix, "sup_" + suffix + "@univ.edu");
        assignRole(itStaffAccount, roleHelpDesk);
        assignDepartment(itStaffAccount, itDept, "SUPPORT");

        deptSupportAccount = createUser("DSU_" + suffix, "dsu_" + suffix + "@univ.edu");
        assignRole(deptSupportAccount, roleDeptSupport);
        assignDepartment(deptSupportAccount, itDept, "SUPPORT");

        adminAccount = createUser("ADM_" + suffix, "adm_" + suffix + "@univ.edu");
        assignRole(adminAccount, roleAdmin);

        itTicket = createTicket(stuProfile, itCat, "IT Network Down");
        finTicket = createTicket(stuProfile, finCat, "Finance Invoice Error");
        unmappedTicket = createTicket(stuProfile, unmappedCat, "Unmapped Request");
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

    private UserAccount createUser(String universityId, String email) {
        UserAccount u = new UserAccount();
        u.setUniversityId(universityId);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("Secret@123"));
        u.setFirstName("MvcTest");
        u.setLastName(universityId);
        u.setAccountStatus(AccountStatus.ACTIVE);
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

    private Ticket createTicket(Student student, Category category, String subject) {
        Ticket t = new Ticket();
        t.setReferenceNo("HD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        t.setStudent(student);
        t.setCategory(category);
        t.setTicketType(TicketType.INCIDENT);
        t.setSubject(subject);
        t.setDescription("Sample issue: " + subject);
        t.setPriority(TicketPriority.MEDIUM);
        t.setStatus(TicketStatus.NEW);
        return ticketRepository.save(t);
    }

    // 1. Staff can view ticket in their own department
    @Test
    void testAuthorizedStaffCanViewOwnDepartmentTicket() throws Exception {
        mockMvc.perform(get("/staff/tickets/" + itTicket.getTicketId())
                        .with(user(itStaffAccount.getUniversityId())
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_Help Desk Support Staff"),
                                        new SimpleGrantedAuthority("VIEW_ALL_TICKETS")
                                )))
                .andExpect(status().isOk())
                .andExpect(view().name("support-ticket-view"))
                .andExpect(model().attributeExists("ticket"));
    }

    // 2. Direct URL access to unauthorized department ticket returns HTTP 403
    @Test
    void testDirectUrlAccessToUnauthorizedDepartmentReturns403() throws Exception {
        mockMvc.perform(get("/staff/tickets/" + finTicket.getTicketId())
                        .with(user(itStaffAccount.getUniversityId())
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_Help Desk Support Staff"),
                                        new SimpleGrantedAuthority("VIEW_ALL_TICKETS")
                                )))
                .andExpect(status().isForbidden());
    }

    // 3. Normal staff cannot access manual-routing unmapped ticket (returns 403)
    @Test
    void testNormalStaffCannotAccessUnmappedTicket() throws Exception {
        mockMvc.perform(get("/staff/tickets/" + unmappedTicket.getTicketId())
                        .with(user(itStaffAccount.getUniversityId())
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_Help Desk Support Staff"),
                                        new SimpleGrantedAuthority("VIEW_ALL_TICKETS")
                                )))
                .andExpect(status().isForbidden());
    }

    // 4. System Administrator can inspect unmapped ticket
    @Test
    void testSystemAdministratorCanInspectUnmappedTicket() throws Exception {
        mockMvc.perform(get("/admin/tickets/" + unmappedTicket.getTicketId())
                        .with(user(adminAccount.getUniversityId())
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_System Administrator"),
                                        new SimpleGrantedAuthority("VIEW_ALL_TICKETS")
                                )))
                .andExpect(status().isOk())
                .andExpect(view().name("support-ticket-view"))
                .andExpect(model().attribute("canAssign", false)); // assignment blocked until routed
    }

    // 5. Role without required authority receives 403
    // Department Support Team Member does NOT have ASSIGN_TICKET authority
    @Test
    void testRoleWithoutRequiredAuthorityReceives403() throws Exception {
        mockMvc.perform(post("/staff/tickets/" + itTicket.getTicketId() + "/assign")
                        .with(csrf())
                        .with(user(deptSupportAccount.getUniversityId())
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_Department Support Team Member"),
                                        new SimpleGrantedAuthority("VIEW_ALL_TICKETS"),
                                        new SimpleGrantedAuthority("UPDATE_TICKET")
                                        // Omitting ASSIGN_TICKET
                                ))
                        .param("assignedToUserId", itStaffAccount.getUserId().toString())
                        .param("priority", "HIGH"))
                .andExpect(status().isForbidden());
    }

    // 6. User Correction 3: INTERNAL comments are NEVER rendered to student (STU001)
    @Test
    void testInternalCommentNeverRenderedToStudent() throws Exception {
        String publicSecret = "PUBLIC_REPLY_VISIBLE_12345";
        String internalSecret = "CONFIDENTIAL_INTERNAL_NOTE_98765";

        commentService.addComment(itTicket.getTicketId(), itStaffAccount.getUniversityId(), publicSecret, CommentType.PUBLIC);
        commentService.addComment(itTicket.getTicketId(), itStaffAccount.getUniversityId(), internalSecret, CommentType.INTERNAL);

        // Fetch ticket detail as student
        mockMvc.perform(get("/student/tickets/" + itTicket.getTicketId())
                        .with(user(stuAccount.getUniversityId())
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_Student"),
                                        new SimpleGrantedAuthority("VIEW_OWN_TICKETS")
                                )))
                .andExpect(status().isOk())
                .andExpect(view().name("student-ticket-view"))
                .andExpect(content().string(containsString(publicSecret)))
                .andExpect(content().string(not(containsString(internalSecret))));
    }

    // 7. CSRF protection verified: POST without CSRF token is rejected with 403
    @Test
    void testCsrfProtectionActiveOnPostActions() throws Exception {
        mockMvc.perform(post("/staff/tickets/" + itTicket.getTicketId() + "/comments")
                        // Missing csrf()
                        .with(user(itStaffAccount.getUniversityId())
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_Help Desk Support Staff"),
                                        new SimpleGrantedAuthority("UPDATE_TICKET")
                                ))
                        .param("comment", "Testing missing CSRF")
                        .param("commentType", "PUBLIC"))
                .andExpect(status().isForbidden());
    }
}
