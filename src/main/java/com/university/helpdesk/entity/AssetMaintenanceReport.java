package com.university.helpdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name = "ASSET_MAINTENANCE_REPORT") @Getter @Setter
public class AssetMaintenanceReport {
    public enum Status { PENDING, APPROVED, MORE_WORK_REQUIRED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Asset_ID", nullable = false)
    private ItAsset asset;
    @Column(nullable = false, length = 50)
    private String submittedBy;
    @Column(nullable = false, length = 2000)
    private String repairNotes;
    @Column(nullable = false)
    private LocalDateTime submittedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Status status = Status.PENDING;
    @Column(length = 50)
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    @Column(length = 2000)
    private String reviewNotes;
}
