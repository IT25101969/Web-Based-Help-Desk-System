package com.university.helpdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "IT_ASSET")
@Getter @Setter
public class ItAsset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Asset_ID")
    private Long assetId;
    @Version
    private Long version;
    @Column(name = "Asset_Tag", nullable = false, unique = true, length = 50)
    private String assetTag;
    @Column(name = "Asset_Name", nullable = false, length = 150)
    private String assetName;
    @Enumerated(EnumType.STRING) @Column(name = "Asset_Type", nullable = false, length = 30)
    private AssetType assetType;
    @Column(name = "Serial_Number", unique = true, length = 100)
    private String serialNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Department_ID", nullable = false)
    private Department department;
    @Column(name = "Location", nullable = false, length = 150)
    private String location;
    @Enumerated(EnumType.STRING) @Column(name = "Asset_Condition", nullable = false, length = 30)
    private AssetCondition condition = AssetCondition.WORKING;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Assigned_User_ID")
    private UserAccount assignedUser;
    @Column(name = "Purchase_Date")
    private LocalDate purchaseDate;
    @Column(name = "Notes", length = 2000)
    private String notes;
    @Column(name = "Created_Date", nullable = false)
    private LocalDateTime createdDate;
    @Column(name = "Updated_Date", nullable = false)
    private LocalDateTime updatedDate;
    @PrePersist void created() { createdDate = LocalDateTime.now(); updatedDate = createdDate; }
    @PreUpdate void updated() { updatedDate = LocalDateTime.now(); }
}
