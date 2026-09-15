package com.university.helpdesk.config;

import com.university.helpdesk.entity.Permission;
import com.university.helpdesk.entity.Role;
import com.university.helpdesk.entity.RolePermission;
import com.university.helpdesk.repository.PermissionRepository;
import com.university.helpdesk.repository.RolePermissionRepository;
import com.university.helpdesk.repository.RoleRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class RbacPermissionInitializer {

    @Bean
    CommandLineRunner initializeRbacPermissions(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository
    ) {
        return args -> {

            Permission submitTicket = permission(
                    permissionRepository,
                    "SUBMIT_TICKET",
                    "Submit incident and service request tickets"
            );

            Permission viewOwnTickets = permission(
                    permissionRepository,
                    "VIEW_OWN_TICKETS",
                    "View tickets created by the logged-in student"
            );

            Permission viewAllTickets = permission(
                    permissionRepository,
                    "VIEW_ALL_TICKETS",
                    "View tickets available to support and administration users"
            );

            Permission assignTicket = permission(
                    permissionRepository,
                    "ASSIGN_TICKET",
                    "Assign or reassign a ticket to support staff"
            );

            Permission updateTicket = permission(
                    permissionRepository,
                    "UPDATE_TICKET",
                    "Update ticket status, priority and comments"
            );

            Permission manageUsers = permission(
                    permissionRepository,
                    "MANAGE_USERS",
                    "Create users, update account status and assign roles"
            );

            Permission manageFaq = permission(
                    permissionRepository,
                    "MANAGE_FAQ",
                    "Create, update, publish and archive FAQ entries"
            );

            Permission viewReports = permission(
                    permissionRepository,
                    "VIEW_REPORTS",
                    "View and export help desk reports"
            );

            Permission manageSystem = permission(
                    permissionRepository,
                    "MANAGE_SYSTEM",
                    "Perform system administration and monitoring actions"
            );

            Map<String, List<Permission>> matrix = new LinkedHashMap<>();

            matrix.put(
                    "Student",
                    List.of(
                            submitTicket,
                            viewOwnTickets
                    )
            );

            matrix.put(
                    "Help Desk Support Staff",
                    List.of(
                            viewAllTickets,
                            assignTicket,
                            updateTicket
                    )
            );

            matrix.put(
                    "Department Support Team Member",
                    List.of(
                            viewAllTickets,
                            updateTicket
                    )
            );

            matrix.put(
                    "Department Manager",
                    List.of(
                            viewAllTickets,
                            assignTicket,
                            updateTicket
                    )
            );

            matrix.put(
                    "System Administrator",
                    List.of(
                            viewAllTickets,
                            assignTicket,
                            updateTicket,
                            manageUsers,
                            manageFaq,
                            viewReports,
                            manageSystem
                    )
            );

            matrix.put(
                    "University Management",
                    List.of(
                            viewReports
                    )
            );

            for (Map.Entry<String, List<Permission>> entry : matrix.entrySet()) {
                Role role = role(
                        roleRepository,
                        entry.getKey()
                );

                for (Permission permission : entry.getValue()) {
                    grant(
                            rolePermissionRepository,
                            role,
                            permission
                    );
                }
            }
        };
    }

    private Role role(
            RoleRepository roleRepository,
            String roleName
    ) {
        return roleRepository.findByRoleName(roleName)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setRoleName(roleName);
                    role.setDescription(roleName);
                    return roleRepository.save(role);
                });
    }

    private Permission permission(
            PermissionRepository permissionRepository,
            String permissionName,
            String description
    ) {
        return permissionRepository.findByPermissionName(permissionName)
                .orElseGet(() -> {
                    Permission permission = new Permission();
                    permission.setPermissionName(permissionName);
                    permission.setDescription(description);
                    return permissionRepository.save(permission);
                });
    }

    private void grant(
            RolePermissionRepository rolePermissionRepository,
            Role role,
            Permission permission
    ) {
        if (rolePermissionRepository
                .existsByRoleRoleIdAndPermissionPermissionId(
                        role.getRoleId(),
                        permission.getPermissionId()
                )) {
            return;
        }

        RolePermission rolePermission = new RolePermission();
        rolePermission.setRole(role);
        rolePermission.setPermission(permission);
        rolePermission.setActive(true);

        rolePermissionRepository.save(rolePermission);
    }
}
