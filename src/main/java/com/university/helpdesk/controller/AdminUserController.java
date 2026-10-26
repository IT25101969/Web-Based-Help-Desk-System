package com.university.helpdesk.controller;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.ActivityLogService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final com.university.helpdesk.service.AdminAccountService accountAccess;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;
    private final com.university.helpdesk.security.AccountSessionService accountSessions;
    private final com.university.helpdesk.service.AccountCreationService accountCreation;
    private final com.university.helpdesk.service.UserProfileService profiles;

    public AdminUserController(
            com.university.helpdesk.service.AdminAccountService accountAccess,
            UserAccountRepository userAccountRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            StudentRepository studentRepository,
            PasswordEncoder passwordEncoder,
            ActivityLogService activityLogService,
            com.university.helpdesk.security.AccountSessionService accountSessions,
            com.university.helpdesk.service.AccountCreationService accountCreation,
            com.university.helpdesk.service.UserProfileService profiles
    ) {
        this.accountAccess = accountAccess;
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityLogService = activityLogService;
        this.accountSessions = accountSessions;
        this.accountCreation = accountCreation;
        this.profiles = profiles;
    }

    @GetMapping
    public String list(Model model, @RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "") String status,
                       @RequestParam(required = false) Long roleId,
                       @RequestParam(defaultValue = "0") int page) {
        List<UserAccount> users = userAccountRepository.findAll();

        Map<Long, List<UserRole>> userRolesMap = new LinkedHashMap<>();
        for (UserAccount user : users) {
            userRolesMap.put(
                    user.getUserId(),
                    userRoleRepository.findByUserUserIdAndActiveTrue(user.getUserId())
            );
        }

        String query = q.trim();
        query = query.substring(0, Math.min(200, query.length()));
        String term = query.toLowerCase(java.util.Locale.ROOT);
        String selectedStatus = java.util.Arrays.stream(AccountStatus.values()).anyMatch(s -> s.name().equals(status)) ? status : "";
        var filtered = users.stream().filter(u -> (u.getUniversityId() + " " + u.getFirstName() + " " + u.getLastName() + " " + u.getEmail()).toLowerCase(java.util.Locale.ROOT).contains(term))
                .filter(u -> selectedStatus.isEmpty() || u.getAccountStatus().name().equals(selectedStatus))
                .filter(u -> roleId == null || userRolesMap.get(u.getUserId()).stream().anyMatch(r -> r.getRole().getRoleId().equals(roleId)))
                .sorted(java.util.Comparator.comparing(UserAccount::getUniversityId, String.CASE_INSENSITIVE_ORDER)).toList();
        int pages = Math.max(1, (filtered.size() + 9) / 10);
        int current = Math.max(0, Math.min(page, pages - 1));
        model.addAttribute("users", filtered.subList(Math.min(current * 10, filtered.size()), Math.min(current * 10 + 10, filtered.size())));
        model.addAttribute("totalUsers", users.size());
        model.addAttribute("activeUsers", users.stream().filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE).count());
        model.addAttribute("lockedUsers", users.stream().filter(u -> u.getAccountStatus() == AccountStatus.LOCKED).count());
        model.addAttribute("disabledUsers", users.stream().filter(u -> u.getAccountStatus() == AccountStatus.DISABLED).count());
        model.addAttribute("q", query); model.addAttribute("selectedStatus", selectedStatus); model.addAttribute("selectedRoleId", roleId);
        model.addAttribute("resultCount", filtered.size()); model.addAttribute("page", current); model.addAttribute("pageCount", pages);
        if (!model.containsAttribute("createValues")) model.addAttribute("createValues", java.util.Map.of());
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("userRolesMap", userRolesMap);
        model.addAttribute("statuses", AccountStatus.values());
        return "admin-users";
    }

    @GetMapping("/{userId}/edit")
    public String edit(@PathVariable Long userId, Model model) {
        var user = userAccountRepository.findById(userId).orElseThrow(() -> new com.university.helpdesk.service.ResourceNotFoundException("User was not found."));
        model.addAttribute("account", user);
        if (!model.containsAttribute("profileValues")) model.addAttribute("profileValues", Map.of("universityId", user.getUniversityId(), "email", user.getEmail(), "firstName", user.getFirstName(), "lastName", user.getLastName()));
        model.addAttribute("assignedRoles", userRoleRepository.findByUserUserIdAndActiveTrue(userId));
        model.addAttribute("roles", roleRepository.findAll()); model.addAttribute("statuses", AccountStatus.values());
        return "admin-user-edit";
    }

    @PostMapping("/{userId}/profile")
    public String updateProfile(@PathVariable Long userId, @RequestParam String universityId, @RequestParam String email,
                                @RequestParam String firstName, @RequestParam String lastName, Authentication authentication,
                                HttpServletRequest request, RedirectAttributes flash) {
        try {
            profiles.update(userId, universityId, email, firstName, lastName, authentication.getName(), request.getRemoteAddr());
            flash.addFlashAttribute("success", "Profile updated successfully. Changes to login details require a new sign-in.");
        } catch (IllegalArgumentException | org.springframework.dao.DataIntegrityViolationException ex) {
            flash.addFlashAttribute("error", ex instanceof IllegalArgumentException ? ex.getMessage() : "These login details are already in use. Choose a different university ID or email.");
            flash.addFlashAttribute("profileValues", Map.of("universityId", universityId, "email", email, "firstName", firstName, "lastName", lastName));
        }
        return "redirect:/admin/users/" + userId + "/edit";
    }

    @PostMapping
    public String create(
            @RequestParam String universityId,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam Long roleId,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            if (universityId == null || universityId.trim().isEmpty()) {
                throw new IllegalArgumentException("University ID is required.");
            }
            if (email == null || email.trim().isEmpty()) {
                throw new IllegalArgumentException("Email is required.");
            }
            if (firstName == null || firstName.trim().isEmpty() || lastName == null || lastName.trim().isEmpty()) {
                throw new IllegalArgumentException("First and last names are required.");
            }
            if (userAccountRepository.existsByUniversityId(universityId.trim())) {
                throw new IllegalArgumentException("University ID already exists.");
            }
            if (userAccountRepository.existsByEmail(email.trim())) {
                throw new IllegalArgumentException("Email already exists.");
            }
            com.university.helpdesk.security.PasswordPolicy.validate(password);

            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new IllegalArgumentException("Role was not found."));

            UserAccount user = new UserAccount();
            user.setUniversityId(universityId.trim());
            user.setEmail(email.trim());
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setFirstName(firstName.trim());
            user.setLastName(lastName.trim());
            user.setAccountStatus(AccountStatus.ACTIVE);
            user = accountCreation.create(user, role);

            UserAccount actor = userAccountRepository
                    .findByUniversityId(authentication.getName())
                    .orElse(null);

            if (actor != null) {
                activityLogService.log(
                        actor,
                        "USER_CREATED",
                        "USER_ACCOUNT",
                        user.getUserId(),
                        request.getRemoteAddr()
                );
            }

            redirectAttributes.addFlashAttribute("success", "User account created.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addFlashAttribute("createValues", Map.of("universityId", universityId, "email", email, "firstName", firstName, "lastName", lastName, "roleId", roleId));
        }

        return "redirect:/admin/users";
    }

    @PostMapping("/{userId}/status")
    public String status(
            @PathVariable Long userId,
            @RequestParam AccountStatus status,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            redirectAttributes.addFlashAttribute("success", accountAccess.status(userId, status, authentication.getName(), request.getRemoteAddr()));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "profile".equals(request.getParameter("returnTo")) ? "redirect:/admin/users/" + userId + "/edit" : "redirect:/admin/users";
    }

    @PostMapping("/{userId}/unlock")
    public String unlock(
            @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            redirectAttributes.addFlashAttribute("success", accountAccess.unlock(userId, authentication.getName(), request.getRemoteAddr()));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "profile".equals(request.getParameter("returnTo")) ? "redirect:/admin/users/" + userId + "/edit" : "redirect:/admin/users";
    }

    @PostMapping("/{userId}/role")
    public String addRole(
            @PathVariable Long userId,
            @RequestParam Long roleId,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            redirectAttributes.addFlashAttribute("success", accountAccess.addRole(userId, roleId, authentication.getName(), request.getRemoteAddr()));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "profile".equals(request.getParameter("returnTo")) ? "redirect:/admin/users/" + userId + "/edit" : "redirect:/admin/users";
    }

    @PostMapping("/{userId}/roles/{roleId}/remove")
    public String removeRole(
            @PathVariable Long userId,
            @PathVariable Long roleId,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            redirectAttributes.addFlashAttribute("success", accountAccess.removeRole(userId, roleId, authentication.getName(), request.getRemoteAddr()));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "profile".equals(request.getParameter("returnTo")) ? "redirect:/admin/users/" + userId + "/edit" : "redirect:/admin/users";
    }

    @PostMapping("/{userId}/delete")
    public String delete(
            @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            redirectAttributes.addFlashAttribute("success", accountAccess.delete(userId, authentication.getName(), request.getRemoteAddr()));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/admin/users";
    }
}

