package com.university.helpdesk.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "FAQ_SEARCH_LOG",
        indexes = {
                @Index(name = "idx_faq_search_log_searched_at", columnList = "Searched_At"),
                @Index(name = "idx_faq_search_log_query", columnList = "Query"),
                @Index(name = "idx_faq_search_log_norm_query", columnList = "Normalized_Query"),
                @Index(name = "idx_faq_search_log_category", columnList = "Category_ID")
        }
)
public class FaqSearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Search_ID")
    private Long searchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "User_ID", foreignKey = @ForeignKey(name = "fk_faq_search_log_user"))
    private UserAccount user;

    @Column(name = "Query", length = 200)
    private String query;

    @Column(name = "Normalized_Query", length = 200)
    private String normalizedQuery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Category_ID", foreignKey = @ForeignKey(name = "fk_faq_search_log_category"))
    private Category category;

    @Column(name = "Results_Count", nullable = false)
    private int resultsCount;

    @Column(name = "Searched_At", nullable = false)
    private LocalDateTime searchedAt;

    @PrePersist
    void onCreate() {
        if (searchedAt == null) {
            searchedAt = LocalDateTime.now();
        }
    }

    public Long getSearchId() {
        return searchId;
    }

    public void setSearchId(Long searchId) {
        this.searchId = searchId;
    }

    public UserAccount getUser() {
        return user;
    }

    public void setUser(UserAccount user) {
        this.user = user;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getNormalizedQuery() {
        return normalizedQuery;
    }

    public void setNormalizedQuery(String normalizedQuery) {
        this.normalizedQuery = normalizedQuery;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public int getResultsCount() {
        return resultsCount;
    }

    public void setResultsCount(int resultsCount) {
        this.resultsCount = resultsCount;
    }

    public LocalDateTime getSearchedAt() {
        return searchedAt;
    }

    public void setSearchedAt(LocalDateTime searchedAt) {
        this.searchedAt = searchedAt;
    }
}
