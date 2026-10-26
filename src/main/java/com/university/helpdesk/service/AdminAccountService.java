package com.university.helpdesk.service;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.security.AccountSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Account access changes and their audit entries commit as one database operation. */
@Service
public class AdminAccountService {
    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final StudentRepository studentRepository;
    private final ActivityLogService activityLogService;
    private final AccountSessionService accountSessions;
    public AdminAccountService(UserAccountRepository users, UserRoleRepository roles, RoleRepository roleCatalog,
            StudentRepository students, ActivityLogService logs, AccountSessionService sessions) {
        userAccountRepository=users; userRoleRepository=roles; roleRepository=roleCatalog;
        studentRepository=students; activityLogService=logs; accountSessions=sessions;
    }
    @Transactional
    public String status(Long userId, AccountStatus status, String actorId, String ip) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));

        UserAccount actor = userAccountRepository
                .findByUniversityId(actorId)
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

        if (status == AccountStatus.ACTIVE) requireActiveRole(userId);
        AccountStatus oldStatus = user.getAccountStatus();
        user.setAccountStatus(status);

        if (status == AccountStatus.ACTIVE) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        } else if (status == AccountStatus.LOCKED) {
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
                ip
        );

        if (status != AccountStatus.ACTIVE) expireAfterCommit(user.getUniversityId());
        return "User status updated.";
    }

    @Transactional
    public String unlock(Long userId, String actorId, String ip) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));

        requireActiveRole(userId);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userAccountRepository.save(user);

        UserAccount actor = userAccountRepository
                .findByUniversityId(actorId)
                .orElse(null);

        if (actor != null) {
            activityLogService.log(
                    actor,
                    "USER_UNLOCKED",
                    "USER_ACCOUNT",
                    userId,
                    ip
            );
        }

        return "User account unlocked.";
    }

    @Transactional
    public String addRole(Long userId, Long roleId, String actorId, String ip) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role was not found."));

        Optional<UserRole> existing = userRoleRepository.findByUserUserIdAndRoleRoleId(userId, roleId);
        if (existing.isPresent()) {
            UserRole userRole = existing.get();
            if (Boolean.TRUE.equals(userRole.getActive())) {
                throw new IllegalArgumentException("Role is already assigned to this user.");
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
                .findByUniversityId(actorId)
                .orElse(null);

        if (actor != null) {
            activityLogService.log(
                    actor,
                    "USER_ROLE_ASSIGNED",
                    "USER_ROLE",
                    userId,
                    ip
            );
        }

        expireAfterCommit(user.getUniversityId());
        return "Role assigned.";
    }

    @Transactional
    public String removeRole(Long userId, Long roleId, String actorId, String ip) {
        UserAccount targetUser = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));

        UserAccount actor = userAccountRepository
                .findByUniversityId(actorId)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));

        UserRole userRole = userRoleRepository.findByUserUserIdAndRoleRoleId(userId, roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role assignment was not found."));

        if (!Boolean.TRUE.equals(userRole.getActive())) {
            throw new IllegalArgumentException("Role is not currently assigned to this user.");
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

        if (targetUser.getAccountStatus() == AccountStatus.ACTIVE &&
                userRoleRepository.findByUserUserIdAndActiveTrue(userId).size() <= 1) {
            throw new IllegalArgumentException("Assign another role or disable the account before removing its last active role.");
        }
        userRole.setActive(false);
        userRoleRepository.save(userRole);

        activityLogService.log(
                actor,
                "USER_ROLE_REMOVED",
                "USER_ROLE",
                userId,
                ip
        );

        expireAfterCommit(targetUser.getUniversityId());
        return "Role removed.";
    }

    @Transactional
    public String delete(Long userId, String actorId, String ip) {
        UserAccount targetUser = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));

        UserAccount actor = userAccountRepository
                .findByUniversityId(actorId)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));

        if (actor.getUserId().equals(targetUser.getUserId())) {
            throw new IllegalArgumentException("Administrators cannot delete their own account.");
        }

        long otherUsableAdmins = userRoleRepository.countActiveUsersWithRoleExcludingUser(
                "System Administrator",
                AccountStatus.ACTIVE,
                targetUser.getUserId()
        );
        boolean isTargetAdmin = userRoleRepository.findByUserUserIdAndActiveTrue(targetUser.getUserId())
                .stream().anyMatch(r -> "System Administrator".equals(r.getRole().getRoleName()));

        if (isTargetAdmin && otherUsableAdmins < 1) {
            throw new IllegalArgumentException("Cannot delete the last usable System Administrator account.");
        }

        targetUser.setAccountStatus(AccountStatus.DISABLED);
        userAccountRepository.save(targetUser);

        List<UserRole> roles = userRoleRepository.findByUserUserId(userId);
        for (UserRole r : roles) {
            r.setActive(false);
            userRoleRepository.save(r);
        }

        activityLogService.log(
                actor,
                "USER_DELETED",
                "USER_ACCOUNT",
                userId,
                ip
        );

        expireAfterCommit(targetUser.getUniversityId());
        return "User account " + targetUser.getUniversityId() + " deactivated / deleted successfully.";
    }
    private void requireActiveRole(Long userId) {
        if (userRoleRepository.findByUserUserIdAndActiveTrue(userId).isEmpty()) {
            throw new IllegalArgumentException("Assign a role before activating or unlocking this account.");
        }
    }

    private void expireAfterCommit(String universityId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { accountSessions.expire(universityId); }
        });
    }
}
