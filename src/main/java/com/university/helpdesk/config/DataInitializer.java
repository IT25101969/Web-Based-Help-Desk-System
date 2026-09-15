package com.university.helpdesk.config;

import com.university.helpdesk.entity.AccountStatus;
import com.university.helpdesk.entity.Role;
import com.university.helpdesk.entity.UserAccount;
import com.university.helpdesk.entity.UserRole;
import com.university.helpdesk.repository.RoleRepository;
import com.university.helpdesk.repository.UserAccountRepository;
import com.university.helpdesk.repository.UserRoleRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initializeData(
            UserAccountRepository userAccountRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder
    ) {

        return args -> {

            Role studentRole = createRoleIfMissing(
                    roleRepository,
                    "Student",
                    "University Student"
            );

            Role helpDeskRole = createRoleIfMissing(
                    roleRepository,
                    "Help Desk Support Staff",
                    "Help Desk Support Staff Member"
            );

            Role departmentSupportRole = createRoleIfMissing(
                    roleRepository,
                    "Department Support Team Member",
                    "Department Support Team Member"
            );

            Role departmentManagerRole = createRoleIfMissing(
                    roleRepository,
                    "Department Manager",
                    "Department Manager"
            );

            Role adminRole = createRoleIfMissing(
                    roleRepository,
                    "System Administrator",
                    "System Administrator"
            );

            Role managementRole = createRoleIfMissing(
                    roleRepository,
                    "University Management",
                    "University Management User"
            );

            createUserIfMissing(
                    userAccountRepository,
                    userRoleRepository,
                    passwordEncoder,
                    studentRole,
                    "STU001",
                    "student@university.edu",
                    "Student@123",
                    "Demo",
                    "Student"
            );

            createUserIfMissing(
                    userAccountRepository,
                    userRoleRepository,
                    passwordEncoder,
                    helpDeskRole,
                    "SUP001",
                    "support@university.edu",
                    "Support@123",
                    "Demo",
                    "Support"
            );

            createUserIfMissing(
                    userAccountRepository,
                    userRoleRepository,
                    passwordEncoder,
                    departmentSupportRole,
                    "DSU001",
                    "departmentsupport@university.edu",
                    "Department@123",
                    "Demo",
                    "Department Support"
            );

            createUserIfMissing(
                    userAccountRepository,
                    userRoleRepository,
                    passwordEncoder,
                    departmentManagerRole,
                    "MGR001",
                    "manager@university.edu",
                    "Manager@123",
                    "Demo",
                    "Manager"
            );

            createUserIfMissing(
                    userAccountRepository,
                    userRoleRepository,
                    passwordEncoder,
                    adminRole,
                    "ADM001",
                    "admin@university.edu",
                    "Admin@123",
                    "Demo",
                    "Administrator"
            );

            createUserIfMissing(
                    userAccountRepository,
                    userRoleRepository,
                    passwordEncoder,
                    managementRole,
                    "UMG001",
                    "management@university.edu",
                    "Management@123",
                    "Demo",
                    "Management"
            );
        };
    }

    private Role createRoleIfMissing(
            RoleRepository roleRepository,
            String roleName,
            String description
    ) {

        return roleRepository.findByRoleName(roleName)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setRoleName(roleName);
                    role.setDescription(description);
                    return roleRepository.save(role);
                });
    }

    private void createUserIfMissing(
            UserAccountRepository userAccountRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            Role role,
            String universityId,
            String email,
            String rawPassword,
            String firstName,
            String lastName
    ) {

        if (userAccountRepository.existsByUniversityId(universityId)) {
            return;
        }

        UserAccount user = new UserAccount();

        user.setUniversityId(universityId);
        user.setEmail(email);
        user.setPasswordHash(
                passwordEncoder.encode(rawPassword)
        );
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setAccountStatus(AccountStatus.ACTIVE);

        user = userAccountRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setActive(true);

        userRoleRepository.save(userRole);
    }
}