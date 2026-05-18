package com.isw2project.classifier;

public enum ClassifierType {

    RANDOM_FOREST("RandomForest"),
    NAIVE_BAYES("NaiveBayes"),
    IBK("IBk");

    private final String displayName;

    ClassifierType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
