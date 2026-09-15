package com.university.helpdesk.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "CATEGORY",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_category_department_name",
                columnNames = {"Department_ID", "Category_Name"}
        )
)
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Category_ID")
    private Long categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Department_ID")
    private Department department;

    @Column(name = "Category_Name", nullable = false, length = 120)
    private String categoryName;

    @Column(name = "Description", length = 500)
    private String description;

    @Column(name = "Status", nullable = false, length = 20)
    private String status = "ACTIVE";

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
