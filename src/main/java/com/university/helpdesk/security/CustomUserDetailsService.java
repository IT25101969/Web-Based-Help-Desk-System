package com.university.helpdesk.security;

import com.university.helpdesk.entity.AccountStatus;
import com.university.helpdesk.entity.RolePermission;
import com.university.helpdesk.entity.UserAccount;
import com.university.helpdesk.entity.UserRole;
import com.university.helpdesk.repository.RolePermissionRepository;
import com.university.helpdesk.repository.UserAccountRepository;
import com.university.helpdesk.repository.UserRoleRepository;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public CustomUserDetailsService(
            UserAccountRepository userAccountRepository,
            UserRoleRepository userRoleRepository,
            RolePermissionRepository rolePermissionRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login)
            throws UsernameNotFoundException {

        // Find user using University ID OR Email
        UserAccount user = userAccountRepository
                .findByUniversityIdOrEmail(login, login)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Invalid username or password"
                        )
                );

        Set<SimpleGrantedAuthority> authorities = new HashSet<>();

        // Get active roles assigned to the user
        List<UserRole> userRoles =
                userRoleRepository
                        .findByUserUserIdAndActiveTrue(
                                user.getUserId()
                        );

        for (UserRole userRole : userRoles) {

            String roleName =
                    userRole.getRole().getRoleName();

            // Add role as Spring Security authority
            authorities.add(
                    new SimpleGrantedAuthority(
                            "ROLE_" + roleName
                    )
            );

            // Get active permissions belonging to this role
            List<RolePermission> permissions =
                    rolePermissionRepository
                            .findByRoleRoleIdAndActiveTrue(
                                    userRole
                                            .getRole()
                                            .getRoleId()
                            );

            for (RolePermission rolePermission : permissions) {

                String permissionName =
                        rolePermission
                                .getPermission()
                                .getPermissionName();

                authorities.add(
                        new SimpleGrantedAuthority(
                                permissionName
                        )
                );
            }
        }

        // Disabled account check
        boolean disabled =
                user.getAccountStatus()
                        == AccountStatus.DISABLED;

        // Temporary locked account check
        boolean locked =
                user.getAccountStatus()
                        == AccountStatus.LOCKED
                        && user.getLockedUntil() != null
                        && user.getLockedUntil()
                        .isAfter(LocalDateTime.now());

        return User
                .withUsername(user.getUniversityId())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(disabled)
                .accountLocked(locked)
                .build();
    }
}