package com.university.helpdesk.repository;

import com.university.helpdesk.entity.UserRole;
import com.university.helpdesk.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUserUserIdAndActiveTrue(Long userId);

    List<UserRole> findByRoleRoleNameAndActiveTrue(String roleName);

    boolean existsByUserUserIdAndRoleRoleId(Long userId, Long roleId);
}