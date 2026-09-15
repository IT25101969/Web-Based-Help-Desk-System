package com.university.helpdesk.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "TICKET_ASSIGNMENT")
public class TicketAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Assignment_ID")
    private Long assignmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Ticket_ID", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Assigned_To_User_ID", nullable = false)
    private UserAccount assignedToUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Assigned_By_User_ID", nullable = false)
    private UserAccount assignedByUser;

    @Column(name = "Assigned_Date", nullable = false)
    private LocalDateTime assignedDate;

    @Column(name = "End_Date")
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 30)
    private AssignmentStatus status = AssignmentStatus.ACTIVE;

    @PrePersist
    void onCreate() { if (assignedDate == null) assignedDate = LocalDateTime.now(); }

    public Long getAssignmentId() { return assignmentId; }
    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }
    public UserAccount getAssignedToUser() { return assignedToUser; }
    public void setAssignedToUser(UserAccount assignedToUser) { this.assignedToUser = assignedToUser; }
    public UserAccount getAssignedByUser() { return assignedByUser; }
    public void setAssignedByUser(UserAccount assignedByUser) { this.assignedByUser = assignedByUser; }
    public LocalDateTime getAssignedDate() { return assignedDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
    public AssignmentStatus getStatus() { return status; }
    public void setStatus(AssignmentStatus status) { this.status = status; }
}
