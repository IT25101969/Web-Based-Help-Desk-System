package com.university.helpdesk.service;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountCreationService {
    private final UserAccountRepository users;
    private final UserRoleRepository roles;
    private final StudentRepository students;
    public AccountCreationService(UserAccountRepository users, UserRoleRepository roles, StudentRepository students) {
        this.users = users; this.roles = roles; this.students = students;
    }

    @Transactional
    public UserAccount create(UserAccount account, Role role) {
        if (account == null || role == null) throw new IllegalArgumentException("Account details and role are required.");
        account.setUniversityId(required(account.getUniversityId(), 50, "University ID"));
        account.setEmail(required(account.getEmail(), 150, "Email"));
        account.setFirstName(required(account.getFirstName(), 100, "First name"));
        account.setLastName(required(account.getLastName(), 100, "Last name"));
        if (!account.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Enter a valid email address.");
        UserAccount saved = users.save(account);
        UserRole membership = new UserRole();
        membership.setUser(saved); membership.setRole(role); membership.setActive(true);
        roles.save(membership);
        if ("Student".equals(role.getRoleName())) {
            Student student = new Student(); student.setUser(saved); students.save(student);
        }
        return saved;
    }
    private static String required(String value, int max, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        String clean = value.trim();
        if (clean.length() > max) throw new IllegalArgumentException(label + " cannot exceed " + max + " characters.");
        return clean;
    }
}
