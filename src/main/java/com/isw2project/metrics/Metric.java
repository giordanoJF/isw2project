package com.isw2project.metrics;

import com.isw2project.gitextractor.GitCommandException;

import java.util.Map;

/**
 * A single metric that can be computed for a Java class snapshot.
 * Each implementation knows its column name and how to compute its value.
 */
public interface Metric {

    /**
     * Returns the column name for this metric as it will appear in the CSV.
     */
    String columnName();

    /**
     * Computes the metric value for the given input.
     *
     * @param input all data needed to compute the metric
     * @return the computed value as a String
     */
    String compute(ClassMetricInput input) throws GitCommandException;
}