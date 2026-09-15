package com.university.helpdesk.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "DEPARTMENT")
public class Department {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Department_ID")
    private Long departmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Parent_Department_ID")
    private Department parentDepartment;

    @Column(name = "Department_Name", nullable = false, unique = true, length = 120)
    private String departmentName;

    @Column(name = "Description", length = 500)
    private String description;

    @Column(name = "Contact_Email", length = 150)
    private String contactEmail;

    @Column(name = "Location", length = 150)
    private String location;

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Department getParentDepartment() { return parentDepartment; }
    public void setParentDepartment(Department parentDepartment) { this.parentDepartment = parentDepartment; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
