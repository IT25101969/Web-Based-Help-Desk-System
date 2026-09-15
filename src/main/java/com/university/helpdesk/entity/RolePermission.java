package com.university.helpdesk.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ROLE_PERMISSION")
@IdClass(RolePermissionId.class)
public class RolePermission {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Role_ID", nullable = false)
    private Role role;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Permission_ID", nullable = false)
    private Permission permission;

    @Column(name = "Granted_Date", nullable = false)
    private LocalDateTime grantedDate;

    @Column(name = "Is_Active", nullable = false)
    private Boolean active = true;

    public RolePermission() {
    }

    @PrePersist
    public void prePersist() {
        if (grantedDate == null) {
            grantedDate = LocalDateTime.now();
        }

        if (active == null) {
            active = true;
        }
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Permission getPermission() {
        return permission;
    }

    public void setPermission(Permission permission) {
        this.permission = permission;
    }

    public LocalDateTime getGrantedDate() {
        return grantedDate;
    }

    public void setGrantedDate(LocalDateTime grantedDate) {
        this.grantedDate = grantedDate;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}