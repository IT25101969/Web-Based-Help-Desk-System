package com.university.helpdesk.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "FAQ")
public class Faq {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FAQ_ID")
    private Long faqId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Category_ID")
    private Category category;

    @Column(name = "Question", nullable = false, length = 300)
    private String question;

    @Lob
    @Column(name = "Answer", nullable = false)
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    private FaqStatus status = FaqStatus.DRAFT;

    @Column(name = "Created_Date", nullable = false)
    private LocalDateTime createdDate;

    @Column(name = "Updated_Date", nullable = false)
    private LocalDateTime updatedDate;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdDate == null) createdDate = now;
        if (updatedDate == null) updatedDate = now;
    }

    @PreUpdate
    void onUpdate() { updatedDate = LocalDateTime.now(); }

    public Long getFaqId() { return faqId; }
    public void setFaqId(Long faqId) { this.faqId = faqId; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public FaqStatus getStatus() { return status; }
    public void setStatus(FaqStatus status) { this.status = status; }
    public LocalDateTime getCreatedDate() { return createdDate; }
    public LocalDateTime getUpdatedDate() { return updatedDate; }
}
