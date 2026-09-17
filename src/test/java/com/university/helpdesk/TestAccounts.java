package com.university.helpdesk;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Map;
import java.util.UUID;

/** Per-test fixtures, rolled back with each integration test transaction. */
public abstract class TestAccounts {
    private static String fixturePasswordHash;
    @Autowired private UserAccountRepository accounts;
    @Autowired private RoleRepository roles;
    @Autowired private UserRoleRepository memberships;
    @Autowired private StudentRepository students;
    @Autowired private PasswordEncoder encoder;

    @BeforeEach
    void createTestAccounts() {
        if (fixturePasswordHash == null) fixturePasswordHash = encoder.encode(UUID.randomUUID().toString());
        Map.of("STU001", "Student", "STU002", "Student",
                "SUP001", "Help Desk Support Staff", "DSU001", "Department Support Team Member",
                "MGR001", "Department Manager", "ADM001", "System Administrator",
                "UMG001", "University Management").forEach((id, roleName) -> {
            if (accounts.existsByUniversityId(id)) return;
            UserAccount user = new UserAccount();
            user.setUniversityId(id);
            user.setEmail(id.toLowerCase() + "@fixtures.invalid");
            user.setPasswordHash(fixturePasswordHash);
            user.setFirstName("Test");
            user.setLastName(id);
            user.setAccountStatus(AccountStatus.ACTIVE);
            user = accounts.save(user);
            UserRole membership = new UserRole();
            membership.setUser(user);
            membership.setRole(roles.findByRoleName(roleName).orElseThrow());
            membership.setActive(true);
            memberships.save(membership);
            if (roleName.equals("Student")) {
                Student student = new Student();
                student.setUser(user);
                students.save(student);
            }
        });
    }
}
