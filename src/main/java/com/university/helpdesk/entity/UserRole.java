package com.university.helpdesk.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "USER_ROLE")
@IdClass(UserRoleId.class)
public class UserRole {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "User_ID", nullable = false)
    private UserAccount user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Role_ID", nullable = false)
    private Role role;

    @Column(name = "Assigned_Date", nullable = false)
    private LocalDateTime assignedDate;

    @Column(name = "Is_Active", nullable = false)
    private Boolean active = true;

    public UserRole() {
    }

    @PrePersist
    public void prePersist() {
        if (assignedDate == null) {
            assignedDate = LocalDateTime.now();
        }

        if (active == null) {
            active = true;
        }
    }

    public UserAccount getUser() {
        return user;
    }

    public void setUser(UserAccount user) {
        this.user = user;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public LocalDateTime getAssignedDate() {
        return assignedDate;
    }

    public void setAssignedDate(LocalDateTime assignedDate) {
        this.assignedDate = assignedDate;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}