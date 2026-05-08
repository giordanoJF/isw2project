package com.isw2project.metrics;

import com.isw2project.model.JavaClassSnapshot;

import java.time.LocalDate;

/**
 * Carries all data needed to compute metrics for a single JavaClassSnapshot.
 * Passed to each Metric implementation so they are decoupled from git internals.
 */
public class ClassMetricInput {

    private final JavaClassSnapshot snapshot;
    private final String gitRef;
    private final LocalDate releaseDate;

    public ClassMetricInput(JavaClassSnapshot snapshot, String gitRef, LocalDate releaseDate) {
        this.snapshot = snapshot;
        this.gitRef = gitRef;
        this.releaseDate = releaseDate;
    }

    public JavaClassSnapshot getSnapshot() { return snapshot; }

    /** The git tag or commit ref at which this snapshot was extracted. */
    public String getGitRef()              { return gitRef; }

    public LocalDate getReleaseDate()      { return releaseDate; }
}