package com.university.helpdesk.repository;

import com.university.helpdesk.entity.AccountStatus;
import com.university.helpdesk.entity.UserRole;
import com.university.helpdesk.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUserUserIdAndActiveTrue(Long userId);

    List<UserRole> findByRoleRoleNameAndActiveTrue(String roleName);

    boolean existsByUserUserIdAndRoleRoleId(Long userId, Long roleId);

    Optional<UserRole> findByUserUserIdAndRoleRoleId(Long userId, Long roleId);

    @Query("SELECT COUNT(DISTINCT ur.user.userId) FROM UserRole ur WHERE ur.role.roleName = :roleName AND ur.active = true AND ur.user.accountStatus = :accountStatus")
    long countActiveUsersWithRole(@Param("roleName") String roleName, @Param("accountStatus") AccountStatus accountStatus);

    @Query("SELECT COUNT(DISTINCT ur.user.userId) FROM UserRole ur WHERE ur.role.roleName = :roleName AND ur.active = true AND ur.user.accountStatus = :accountStatus AND ur.user.userId <> :excludedUserId")
    long countActiveUsersWithRoleExcludingUser(@Param("roleName") String roleName, @Param("accountStatus") AccountStatus accountStatus, @Param("excludedUserId") Long excludedUserId);
}