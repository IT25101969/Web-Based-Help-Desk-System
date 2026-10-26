package com.university.helpdesk.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
public class TicketDraft {
    @Id private Long userId;
    private Long categoryId;
    private Long assetId;
    public Long getAssetId(){return assetId;} public void setAssetId(Long v){assetId=v;}
    @Enumerated(EnumType.STRING) private TicketType ticketType;
    @Column(length=200) private String subject;
    @Lob private String description;
    @Column(length=150) private String incidentDetail;
    @Column(length=150) private String serviceDetail;
    @Column(length=20) private String severity;
    private LocalDateTime savedAt;
    @Version private Long version;
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Long getCategoryId(){return categoryId;} public void setCategoryId(Long v){categoryId=v;}
    public TicketType getTicketType(){return ticketType;} public void setTicketType(TicketType v){ticketType=v;}
    public String getSubject(){return subject;} public void setSubject(String v){subject=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getIncidentDetail(){return incidentDetail;} public void setIncidentDetail(String v){incidentDetail=v;}
    public String getServiceDetail(){return serviceDetail;} public void setServiceDetail(String v){serviceDetail=v;}
    public String getSeverity(){return severity;} public void setSeverity(String v){severity=v;}
    public LocalDateTime getSavedAt(){return savedAt;} public void setSavedAt(LocalDateTime v){savedAt=v;}
}
