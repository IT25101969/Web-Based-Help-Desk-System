package com.university.helpdesk.dto;

import com.university.helpdesk.entity.FaqSearchLog;
import java.util.List;

public class FaqSearchAnalyticsSummary {

    private final long totalSearches;
    private final long zeroResultSearches;
    private final List<FaqTopSearchTermDto> topSearchTerms;
    private final List<FaqContentGapDto> contentGaps;
    private final List<FaqCategoryDistributionDto> categoryDistribution;
    private final List<FaqSearchLog> recentSearches;

    public FaqSearchAnalyticsSummary(
            long totalSearches,
            long zeroResultSearches,
            List<FaqTopSearchTermDto> topSearchTerms,
            List<FaqContentGapDto> contentGaps,
            List<FaqCategoryDistributionDto> categoryDistribution,
            List<FaqSearchLog> recentSearches
    ) {
        this.totalSearches = totalSearches;
        this.zeroResultSearches = zeroResultSearches;
        this.topSearchTerms = topSearchTerms;
        this.contentGaps = contentGaps;
        this.categoryDistribution = categoryDistribution;
        this.recentSearches = recentSearches;
    }

    public long getTotalSearches() {
        return totalSearches;
    }

    public long getZeroResultSearches() {
        return zeroResultSearches;
    }

    public List<FaqTopSearchTermDto> getTopSearchTerms() {
        return topSearchTerms;
    }

    public List<FaqContentGapDto> getContentGaps() {
        return contentGaps;
    }

    public List<FaqCategoryDistributionDto> getCategoryDistribution() {
        return categoryDistribution;
    }

    public List<FaqSearchLog> getRecentSearches() {
        return recentSearches;
    }
}
