package com.university.helpdesk.entity;

public enum AssetType {
    LAPTOP("Laptop"), DESKTOP("Desktop computer"), PRINTER("Printer"),
    PROJECTOR("Projector"), NETWORK_DEVICE("Network equipment"), OTHER("Other equipment");
    private final String label;
    AssetType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
