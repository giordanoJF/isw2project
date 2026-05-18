package com.isw2project.classifier;

public enum FeatureSelectionStrategy {

    NONE("No"),
    INFO_GAIN("InfoGain"),
    SPEARMAN("Spearman"),
    FORWARD_SEARCH("ForwardSearch"),
    BACKWARD_SEARCH("BackwardSearch");

    private final String displayName;

    FeatureSelectionStrategy(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
