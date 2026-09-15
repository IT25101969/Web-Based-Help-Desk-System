package com.university.helpdesk.repository;

import com.university.helpdesk.entity.RolePermission;
import com.university.helpdesk.entity.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolePermissionRepository
        extends JpaRepository<RolePermission, RolePermissionId> {

    List<RolePermission> findByRoleRoleIdAndActiveTrue(Long roleId);
}