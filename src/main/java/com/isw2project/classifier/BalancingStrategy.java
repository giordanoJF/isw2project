package com.isw2project.classifier;

public enum BalancingStrategy {

    NONE("No"),
    UNDERSAMPLING("Undersampling"),
    OVERSAMPLING("Oversampling"),
    SMOTE("SMOTE");

    private final String displayName;

    BalancingStrategy(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
