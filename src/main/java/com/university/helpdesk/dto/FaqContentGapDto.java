package com.university.helpdesk.dto;

public class FaqContentGapDto {

    private final String displayTerm;
    private final String normalizedTerm;
    private final Long searchCount;

    public FaqContentGapDto(
            String displayTerm,
            String normalizedTerm,
            Long searchCount
    ) {
        this.displayTerm = displayTerm;
        this.normalizedTerm = normalizedTerm;
        this.searchCount = searchCount;
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
}
