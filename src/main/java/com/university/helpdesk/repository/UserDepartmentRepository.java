package com.university.helpdesk.repository;

import com.university.helpdesk.entity.UserDepartment;
import com.university.helpdesk.entity.UserDepartmentId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserDepartmentRepository extends JpaRepository<UserDepartment, UserDepartmentId> {
    java.util.Optional<UserDepartment> findByUserUserIdAndDepartmentDepartmentId(Long userId, Long departmentId);
    List<UserDepartment> findByActiveTrue();
    List<UserDepartment> findByDepartmentDepartmentIdAndActiveTrue(Long departmentId);
    List<UserDepartment> findByUserUserIdAndActiveTrue(Long userId);
    boolean existsByUserUserIdAndDepartmentDepartmentIdAndActiveTrue(Long userId, Long departmentId);
}
