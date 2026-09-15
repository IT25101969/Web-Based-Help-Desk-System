package com.university.helpdesk.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "INCIDENT")
public class Incident {
    @Id
    @Column(name = "Ticket_ID")
    private Long ticketId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "Ticket_ID")
    private Ticket ticket;

    @Column(name = "Incident_Type", length = 120)
    private String incidentType;

    @Column(name = "Severity", length = 30)
    private String severity;

    public Long getTicketId() { return ticketId; }
    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }
    public String getIncidentType() { return incidentType; }
    public void setIncidentType(String incidentType) { this.incidentType = incidentType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
}
