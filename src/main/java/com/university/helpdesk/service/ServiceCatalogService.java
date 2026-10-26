package com.university.helpdesk.service;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
public class ServiceCatalogService {
    private final DepartmentRepository departments;
    private final CategoryRepository categories;
    private final TicketAssignmentRepository assignments;
    private final UserAccountRepository accounts;
    private final ActivityLogService logs;
    private final UserDepartmentRepository memberships;
    private final UserRoleRepository roles;
    public ServiceCatalogService(DepartmentRepository departments, CategoryRepository categories,
            TicketAssignmentRepository assignments, UserAccountRepository accounts, ActivityLogService logs,
            UserDepartmentRepository memberships, UserRoleRepository roles) {
        this.departments = departments; this.categories = categories; this.assignments = assignments;
        this.accounts = accounts; this.logs = logs;
        this.memberships = memberships; this.roles = roles;
    }
    @Transactional
    public Department saveDepartment(Long id, String name, String description, String email, String location, String actor, String ip) {
        name = required(name, 120, "Department name");
        description = optional(description, 500, "Description");
        email = optional(email, 150, "Contact email");
        location = optional(location, 150, "Location");
        if (!email.isEmpty() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Enter a valid contact email.");
        if (departments.existsByDepartmentNameIgnoreCaseAndDepartmentIdNot(name, id == null ? -1L : id))
            throw new IllegalArgumentException("A department with this name already exists.");
        Department department = id == null ? new Department() : departments.findById(id).orElseThrow(() -> new IllegalArgumentException("Department was not found."));
        department.setDepartmentName(name); department.setDescription(description); department.setContactEmail(email); department.setLocation(location);
        departments.saveAndFlush(department);
        logs.log(accounts.findByUniversityId(actor).orElseThrow(), id == null ? "DEPARTMENT_CREATED" : "DEPARTMENT_UPDATED", "DEPARTMENT", department.getDepartmentId(), ip);
        return department;
    }
    @Transactional
    public Category saveCategory(Long id, String name, String description, Long departmentId, String status, String actor, String ip) {
        name = required(name, 120, "Category name"); description = optional(description, 500, "Description");
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) throw new IllegalArgumentException("Choose an active or inactive status.");
        Department department = departmentId == null ? null : departments.findById(departmentId).orElseThrow(() -> new IllegalArgumentException("Department was not found."));
        if (categories.hasDuplicateName(name, departmentId, id == null ? -1L : id)) throw new IllegalArgumentException("This category name already exists in the selected department.");
        Category category = id == null ? new Category() : categories.findById(id).orElseThrow(() -> new IllegalArgumentException("Category was not found."));
        Long currentDepartment = category.getDepartment() == null ? null : category.getDepartment().getDepartmentId();
        if (id != null && !Objects.equals(currentDepartment, departmentId)
                && assignments.existsByTicketCategoryCategoryIdAndStatus(id, AssignmentStatus.ACTIVE))
            throw new IllegalArgumentException("Unassign this category’s tickets before changing its department, so existing assignees do not lose access.");
        category.setCategoryName(name); category.setDescription(description); category.setDepartment(department); category.setStatus(status);
        categories.saveAndFlush(category);
        logs.log(accounts.findByUniversityId(actor).orElseThrow(), id == null ? "CATEGORY_CREATED" : "CATEGORY_UPDATED", "CATEGORY", category.getCategoryId(), ip);
        return category;
    }
    @Transactional
    public UserDepartment updateMembership(Long departmentId, Long userId, boolean active, String actor, String ip) {
        Department department = departments.findById(departmentId).orElseThrow(() -> new IllegalArgumentException("Department was not found."));
        UserAccount user = accounts.findById(userId).orElseThrow(() -> new IllegalArgumentException("Account was not found."));
        if (active && (user.getAccountStatus() != AccountStatus.ACTIVE || roles.findByUserUserIdAndActiveTrue(userId).stream()
                .noneMatch(r -> java.util.Set.of("Help Desk Support Staff", "Department Support Team Member", "Department Manager").contains(r.getRole().getRoleName()))))
            throw new IllegalArgumentException("Choose an active support staff or department manager account.");
        if (!active && assignments.existsByAssignedToUserUserIdAndTicketCategoryDepartmentDepartmentIdAndStatus(userId, departmentId, AssignmentStatus.ACTIVE))
            throw new IllegalArgumentException("Unassign this team member’s department tickets before removing their access.");
        UserDepartment membership = memberships.findByUserUserIdAndDepartmentDepartmentId(userId, departmentId).orElseGet(UserDepartment::new);
        membership.setUser(user); membership.setDepartment(department); membership.setActive(active);
        memberships.saveAndFlush(membership);
        logs.log(accounts.findByUniversityId(actor).orElseThrow(), active ? "DEPARTMENT_MEMBER_ADDED" : "DEPARTMENT_MEMBER_REMOVED", "USER_ACCOUNT", userId, ip);
        return membership;
    }
    private static String required(String value, int max, String label) {
        String clean = optional(value, max, label);
        if (clean.isEmpty()) throw new IllegalArgumentException(label + " is required.");
        return clean;
    }
    private static String optional(String value, int max, String label) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() > max) throw new IllegalArgumentException(label + " must be " + max + " characters or fewer.");
        return clean;
    }
}
