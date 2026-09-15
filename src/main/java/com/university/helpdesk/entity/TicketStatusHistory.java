package com.university.helpdesk.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "TICKET_STATUS_HISTORY")
public class TicketStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Status_History_ID")
    private Long statusHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Ticket_ID", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Changed_By_User_ID")
    private UserAccount changedByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "Old_Status", length = 30)
    private TicketStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "New_Status", nullable = false, length = 30)
    private TicketStatus newStatus;

    @Column(name = "Changed_Date", nullable = false)
    private LocalDateTime changedDate;

    @Column(name = "Reason", length = 500)
    private String reason;

    @PrePersist
    void onCreate() { if (changedDate == null) changedDate = LocalDateTime.now(); }

    public Long getStatusHistoryId() { return statusHistoryId; }
    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }
    public UserAccount getChangedByUser() { return changedByUser; }
    public void setChangedByUser(UserAccount changedByUser) { this.changedByUser = changedByUser; }
    public TicketStatus getOldStatus() { return oldStatus; }
    public void setOldStatus(TicketStatus oldStatus) { this.oldStatus = oldStatus; }
    public TicketStatus getNewStatus() { return newStatus; }
    public void setNewStatus(TicketStatus newStatus) { this.newStatus = newStatus; }
    public LocalDateTime getChangedDate() { return changedDate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
