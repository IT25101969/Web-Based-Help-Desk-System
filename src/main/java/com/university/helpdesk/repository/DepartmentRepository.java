package com.university.helpdesk.repository;

import com.university.helpdesk.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    boolean existsByDepartmentNameIgnoreCaseAndDepartmentIdNot(String name, Long id);
    java.util.List<Department> findAllByOrderByDepartmentNameAsc();
    Optional<Department> findByDepartmentName(String departmentName);
}
