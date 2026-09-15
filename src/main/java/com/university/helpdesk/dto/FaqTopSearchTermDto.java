package com.university.helpdesk.dto;

public class FaqTopSearchTermDto {

    private final String displayTerm;
    private final String normalizedTerm;
    private final Long searchCount;
    private final Long zeroResultCount;
    private final Double avgResultCount;

    public FaqTopSearchTermDto(
            String displayTerm,
            String normalizedTerm,
            Long searchCount,
            Long zeroResultCount,
            Double avgResultCount
    ) {
        this.displayTerm = displayTerm;
        this.normalizedTerm = normalizedTerm;
        this.searchCount = searchCount;
        this.zeroResultCount = zeroResultCount;
        this.avgResultCount = avgResultCount;
    }

    public String getDisplayTerm() {
        return displayTerm;
    }

    public String getNormalizedTerm() {
        return normalizedTerm;
    }

    public Long getSearchCount() {
        return searchCount;
    }

    public Long getZeroResultCount() {
        return zeroResultCount;
    }

    public Double getAvgResultCount() {
        return avgResultCount;
    }
}
