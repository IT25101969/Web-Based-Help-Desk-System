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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        Map<Long, List<String>> roleMap = new LinkedHashMap<>();
        for (UserAccount user : users) {
            roleMap.put(
                    user.getUserId(),
                    userRoleRepository.findByUserUserIdAndActiveTrue(user.getUserId())
                            .stream()
                            .map(userRole -> userRole.getRole().getRoleName())
                            .toList()
            );
        }

        model.addAttribute("users", users);
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("roleMap", roleMap);
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
            RedirectAttributes redirectAttributes
    ) {
        try {
            if (userAccountRepository.existsByUniversityId(universityId.trim())) {
                throw new IllegalArgumentException("University ID already exists.");
            }
            if (userAccountRepository.existsByEmail(email.trim())) {
                throw new IllegalArgumentException("Email already exists.");
            }
            if (password.length() < 8) {
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
            userRoleRepository.save(userRole);

            if ("Student".equals(role.getRoleName())) {
                Student student = new Student();
                student.setUser(user);
                studentRepository.save(student);
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
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));

        user.setAccountStatus(status);

        if (status == AccountStatus.ACTIVE) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        userAccountRepository.save(user);

        UserAccount actor = userAccountRepository
                .findByUniversityId(authentication.getName())
                .orElse(null);

        if (actor != null) {
            activityLogService.log(
                    actor,
                    "USER_STATUS_CHANGED",
                    "USER_ACCOUNT",
                    userId,
                    request.getRemoteAddr()
            );
        }

        redirectAttributes.addFlashAttribute("success", "User status updated.");
        return "redirect:/admin/users";
    }

    @PostMapping("/{userId}/role")
    public String addRole(
            @PathVariable Long userId,
            @RequestParam Long roleId,
            RedirectAttributes redirectAttributes
    ) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role was not found."));

        if (!userRoleRepository.existsByUserUserIdAndRoleRoleId(userId, roleId)) {
            UserRole userRole = new UserRole();
            userRole.setUser(user);
            userRole.setRole(role);
            userRole.setActive(true);
            userRoleRepository.save(userRole);
        }

        redirectAttributes.addFlashAttribute("success", "Role assigned.");
        return "redirect:/admin/users";
    }
}
