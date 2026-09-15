package com.university.helpdesk.entity;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Entity
@Table(name = "NOTIFICATION_PREFERENCE")
public class NotificationPreference implements Persistable<Long> {

    @Id
    @Column(name = "User_ID")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "User_ID")
    private UserAccount user;

    @Column(name = "In_App_Enabled", nullable = false)
    private Boolean inAppEnabled = true;

    @Column(name = "Email_Enabled", nullable = false)
    private Boolean emailEnabled = true;

    @Column(name = "Created_At", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "Updated_At", nullable = false)
    private LocalDateTime updatedAt;

    @Transient
    private boolean isNew = true;

    public NotificationPreference() {
    }

    public NotificationPreference(UserAccount user) {
        this.user = user;
        this.userId = user != null ? user.getUserId() : null;
        this.inAppEnabled = true;
        this.emailEnabled = true;
        this.isNew = true;
    }

    @Override
    public Long getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    public void markNotNew() {
        this.isNew = false;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (inAppEnabled == null) {
            inAppEnabled = true;
        }
        if (emailEnabled == null) {
            emailEnabled = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public UserAccount getUser() {
        return user;
    }

    public void setUser(UserAccount user) {
        this.user = user;
        if (user != null) {
            this.userId = user.getUserId();
        }
    }

    public Boolean getInAppEnabled() {
        return inAppEnabled;
    }

    public void setInAppEnabled(Boolean inAppEnabled) {
        this.inAppEnabled = inAppEnabled != null ? inAppEnabled : true;
    }

    public Boolean getEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(Boolean emailEnabled) {
        this.emailEnabled = emailEnabled != null ? emailEnabled : true;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
