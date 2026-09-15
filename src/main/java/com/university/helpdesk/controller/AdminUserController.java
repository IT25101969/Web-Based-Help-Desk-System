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

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;

    public AdminUserController(
            UserAccountRepository userAccountRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            StudentRepository studentRepository,
            PasswordEncoder passwordEncoder,
            ActivityLogService activityLogService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityLogService = activityLogService;
    }

    @GetMapping
    public String list(Model model) {
        List<UserAccount> users = userAccountRepository.findAll();

        Map<Long, List<UserRole>> userRolesMap = new LinkedHashMap<>();
        for (UserAccount user : users) {
            userRolesMap.put(
                    user.getUserId(),
                    userRoleRepository.findByUserUserIdAndActiveTrue(user.getUserId())
            );
        }

        model.addAttribute("users", users);
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("userRolesMap", userRolesMap);
        model.addAttribute("statuses", AccountStatus.values());
        return "admin-users";
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
            if (password == null || password.length() < 8) {
                throw new IllegalArgumentException("Password must contain at least 8 characters.");
            }

            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new IllegalArgumentException("Role was not found."));

            UserAccount user = new UserAccount();
            user.setUniversityId(universityId.trim());
            user.setEmail(email.trim());
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setFirstName(firstName.trim());
            user.setLastName(lastName.trim());
            user.setAccountStatus(AccountStatus.ACTIVE);
            user = userAccountRepository.save(user);

            UserRole userRole = new UserRole();
            userRole.setUser(user);
            userRole.setRole(role);
            userRole.setActive(true);
            userRole.setAssignedDate(LocalDateTime.now());
            userRoleRepository.save(userRole);

            if ("Student".equals(role.getRoleName())) {
                Student student = new Student();
                student.setUser(user);
                studentRepository.save(student);
            }

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
            UserAccount user = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User was not found."));

            UserAccount actor = userAccountRepository
                    .findByUniversityId(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));

            if (actor.getUserId().equals(user.getUserId()) && (status == AccountStatus.DISABLED || status == AccountStatus.LOCKED)) {
                throw new IllegalArgumentException("Administrators cannot disable or lock their own account.");
            }

            boolean isUsableAdmin = user.getAccountStatus() == AccountStatus.ACTIVE &&
                    userRoleRepository.findByUserUserIdAndActiveTrue(user.getUserId())
                            .stream()
                            .anyMatch(ur -> "System Administrator".equalsIgnoreCase(ur.getRole().getRoleName()));

            if (isUsableAdmin && status != AccountStatus.ACTIVE) {
                long otherUsableAdmins = userRoleRepository.countActiveUsersWithRoleExcludingUser(
                        "System Administrator",
                        AccountStatus.ACTIVE,
                        user.getUserId()
                );
                if (otherUsableAdmins < 1) {
                    throw new IllegalArgumentException("Cannot disable or lock the last usable System Administrator account.");
                }
            }

            AccountStatus oldStatus = user.getAccountStatus();
            user.setAccountStatus(status);

            if (status == AccountStatus.ACTIVE) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
            }

            userAccountRepository.save(user);

            String action = (oldStatus == AccountStatus.LOCKED && status == AccountStatus.ACTIVE)
                    ? "USER_UNLOCKED"
                    : "USER_STATUS_CHANGED";

            activityLogService.log(
                    actor,
                    action,
                    "USER_ACCOUNT",
                    userId,
                    request.getRemoteAddr()
            );

            redirectAttributes.addFlashAttribute("success", "User status updated.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/admin/users";
    }

    @PostMapping("/{userId}/unlock")
    public String unlock(
            @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            UserAccount user = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User was not found."));

            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userAccountRepository.save(user);

            UserAccount actor = userAccountRepository
                    .findByUniversityId(authentication.getName())
                    .orElse(null);

            if (actor != null) {
                activityLogService.log(
                        actor,
                        "USER_UNLOCKED",
                        "USER_ACCOUNT",
                        userId,
                        request.getRemoteAddr()
                );
            }

            redirectAttributes.addFlashAttribute("success", "User account unlocked.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/admin/users";
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
            UserAccount user = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User was not found."));

            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new IllegalArgumentException("Role was not found."));

            Optional<UserRole> existing = userRoleRepository.findByUserUserIdAndRoleRoleId(userId, roleId);
            if (existing.isPresent()) {
                UserRole userRole = existing.get();
                if (Boolean.TRUE.equals(userRole.getActive())) {
                    redirectAttributes.addFlashAttribute("error", "Role is already assigned to this user.");
                    return "redirect:/admin/users";
                } else {
                    userRole.setActive(true);
                    userRole.setAssignedDate(LocalDateTime.now());
                    userRoleRepository.save(userRole);
                }
            } else {
                UserRole userRole = new UserRole();
                userRole.setUser(user);
                userRole.setRole(role);
                userRole.setActive(true);
                userRole.setAssignedDate(LocalDateTime.now());
                userRoleRepository.save(userRole);
            }

            if ("Student".equalsIgnoreCase(role.getRoleName()) && !studentRepository.existsById(userId)) {
                Student student = new Student();
                student.setUser(user);
                studentRepository.save(student);
            }

            UserAccount actor = userAccountRepository
                    .findByUniversityId(authentication.getName())
                    .orElse(null);

            if (actor != null) {
                activityLogService.log(
                        actor,
                        "USER_ROLE_ASSIGNED",
                        "USER_ROLE",
                        userId,
                        request.getRemoteAddr()
                );
            }

            redirectAttributes.addFlashAttribute("success", "Role assigned.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/admin/users";
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
            UserAccount targetUser = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User was not found."));

            UserAccount actor = userAccountRepository
                    .findByUniversityId(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));

            UserRole userRole = userRoleRepository.findByUserUserIdAndRoleRoleId(userId, roleId)
                    .orElseThrow(() -> new IllegalArgumentException("Role assignment was not found."));

            if (!Boolean.TRUE.equals(userRole.getActive())) {
                redirectAttributes.addFlashAttribute("error", "Role is not currently assigned to this user.");
                return "redirect:/admin/users";
            }

            if ("System Administrator".equalsIgnoreCase(userRole.getRole().getRoleName())) {
                if (actor.getUserId().equals(targetUser.getUserId())) {
                    throw new IllegalArgumentException("Administrators cannot remove their own System Administrator role.");
                }
                if (targetUser.getAccountStatus() == AccountStatus.ACTIVE) {
                    long otherUsableAdmins = userRoleRepository.countActiveUsersWithRoleExcludingUser(
                            "System Administrator",
                            AccountStatus.ACTIVE,
                            targetUser.getUserId()
                    );
                    if (otherUsableAdmins < 1) {
                        throw new IllegalArgumentException("Cannot remove the role from the last usable System Administrator account.");
                    }
                }
            }

            userRole.setActive(false);
            userRoleRepository.save(userRole);

            activityLogService.log(
                    actor,
                    "USER_ROLE_REMOVED",
                    "USER_ROLE",
                    userId,
                    request.getRemoteAddr()
            );

            redirectAttributes.addFlashAttribute("success", "Role removed.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/admin/users";
    }
}

