package com.university.helpdesk.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "SERVICE_REQUEST")
public class ServiceRequest {
    @Id
    @Column(name = "Ticket_ID")
    private Long ticketId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "Ticket_ID")
    private Ticket ticket;

    @Column(name = "Requested_Service", length = 150)
    private String requestedService;

    @Column(name = "Requested_Date")
    private LocalDate requestedDate;

    public Long getTicketId() { return ticketId; }
    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }
    public String getRequestedService() { return requestedService; }
    public void setRequestedService(String requestedService) { this.requestedService = requestedService; }
    public LocalDate getRequestedDate() { return requestedDate; }
    public void setRequestedDate(LocalDate requestedDate) { this.requestedDate = requestedDate; }
}
