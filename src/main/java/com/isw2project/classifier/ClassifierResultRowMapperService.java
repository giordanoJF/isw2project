package com.isw2project.classifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps a completed evaluation result to a CSV row (LinkedHashMap preserves column order).
 * Kept in classifier rather than csv to avoid a classifier ↔ csv dependency cycle.
 */
public class ClassifierResultRowMapperService {

    public Map<String, String> toRow(String dataset,
                                     ClassifierType type,
                                     FeatureSelectionStrategy fs,
                                     BalancingStrategy bal,
                                     double[] metrics) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("Dataset",    dataset);
        row.put("Classifier", type.getDisplayName());
        row.put("FS",         fs.getDisplayName());
        row.put("Balancing",  bal.getDisplayName());
        row.put("Precision",  fmt(metrics[0]));
        row.put("Recall",     fmt(metrics[1]));
        row.put("AUC",        fmt(metrics[2]));
        row.put("Kappa",      fmt(metrics[3]));
        row.put("NPofB20",    fmt(metrics[4]));
        return row;
    }

    private String fmt(double v) {
        return String.format("%.2f", v);
    }
}
