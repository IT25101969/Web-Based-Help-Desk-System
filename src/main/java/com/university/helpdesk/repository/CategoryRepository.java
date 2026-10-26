package com.university.helpdesk.repository;

import com.university.helpdesk.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(c) > 0 FROM Category c LEFT JOIN c.department d WHERE LOWER(c.categoryName) = LOWER(:name) AND c.categoryId <> :id AND ((:departmentId IS NULL AND d IS NULL) OR d.departmentId = :departmentId)")
    boolean hasDuplicateName(@org.springframework.data.repository.query.Param("name") String name,
            @org.springframework.data.repository.query.Param("departmentId") Long departmentId, @org.springframework.data.repository.query.Param("id") Long id);
    java.util.List<Category> findAllByOrderByCategoryNameAsc();
    List<Category> findByStatusIgnoreCaseOrderByCategoryNameAsc(String status);
    java.util.Optional<Category> findByCategoryName(String categoryName);
}
