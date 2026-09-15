package com.university.helpdesk.dto;

public class FaqCategoryDistributionDto {

    private final String categoryName;
    private final Long searchCount;

    public FaqCategoryDistributionDto(String categoryName, Long searchCount) {
        this.categoryName = categoryName;
        this.searchCount = searchCount;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public Long getSearchCount() {
        return searchCount;
    }
}
