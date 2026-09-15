package com.university.helpdesk.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "FEEDBACK",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_feedback_ticket",
                columnNames = "Ticket_ID"
        )
)
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Feedback_ID")
    private Long feedbackId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Ticket_ID", nullable = false, unique = true)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Student_ID", nullable = false)
    private UserAccount student;

    @Column(name = "Rating", nullable = false)
    private Integer rating;

    @Column(name = "Comment", length = 1000)
    private String comment;

    @Column(name = "Submitted_Date", nullable = false)
    private LocalDateTime submittedDate;

    @PrePersist
    void onCreate() {
        if (submittedDate == null) submittedDate = LocalDateTime.now();
    }

    public Long getFeedbackId() { return feedbackId; }
    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }
    public UserAccount getStudent() { return student; }
    public void setStudent(UserAccount student) { this.student = student; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getSubmittedDate() { return submittedDate; }
}
