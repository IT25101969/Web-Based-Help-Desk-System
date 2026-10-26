package com.university.helpdesk.entity;

public enum AssetCondition {
    WORKING("Working"), UNDER_MAINTENANCE("Under maintenance"),
    OUT_OF_SERVICE("Out of service"), RETIRED("Retired");
    private final String label;
    AssetCondition(String label) { this.label = label; }
    public String getLabel() { return label; }
}
