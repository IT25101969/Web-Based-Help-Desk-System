package com.university.helpdesk.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "USER_DEPARTMENT")
@IdClass(UserDepartmentId.class)
public class UserDepartment {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "User_ID", nullable = false)
    private UserAccount user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Department_ID", nullable = false)
    private Department department;

    @Column(name = "Membership_Type", nullable = false, length = 40)
    private String membershipType = "MEMBER";

    @Column(name = "Joined_Date", nullable = false)
    private LocalDateTime joinedDate;

    @Column(name = "Is_Active", nullable = false)
    private Boolean active = true;

    @PrePersist
    void onCreate() {
        if (joinedDate == null) joinedDate = LocalDateTime.now();
        if (active == null) active = true;
    }

    public UserAccount getUser() { return user; }
    public void setUser(UserAccount user) { this.user = user; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public String getMembershipType() { return membershipType; }
    public void setMembershipType(String membershipType) { this.membershipType = membershipType; }
    public LocalDateTime getJoinedDate() { return joinedDate; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
