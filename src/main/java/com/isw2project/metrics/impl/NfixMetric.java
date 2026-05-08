package com.isw2project.metrics.impl;

import com.isw2project.gitextractor.GitCommandException;
import com.isw2project.metrics.ClassMetricInput;
import com.isw2project.metrics.Metric;
import com.isw2project.model.Issue;
import com.isw2project.model.Version;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Number of defect fixes: issues whose fix version matches this release
 * and whose commits touched this file.
 */
public class NfixMetric implements Metric {

    private final List<Issue> issues;
    private final Map<String, Set<String>> issueToFilesIndex;

    public NfixMetric(List<Issue> issues, Map<String, Set<String>> issueToFilesIndex) {
        this.issues = issues;
        this.issueToFilesIndex = issueToFilesIndex;
    }

    @Override
    public String columnName() { return "Nfix"; }

    @Override
    public String compute(ClassMetricInput input) throws GitCommandException {
        String release = input.getSnapshot().getRelease();
        String classPath = input.getSnapshot().getClassPath();

        long count = issues.stream()
                .filter(issue -> hasFixVersion(issue, release))
                .filter(issue -> touchesFile(issue.getKey(), classPath))
                .count();

        return String.valueOf(count);
    }

    private boolean hasFixVersion(Issue issue, String release) {
        List<Version> fixVersions = issue.getFields().getFixVersions();
        if (fixVersions == null) return false;
        return fixVersions.stream().anyMatch(v -> release.equals(v.getName()));
    }

    private boolean touchesFile(String issueKey, String classPath) {
        Set<String> files = issueToFilesIndex.get(issueKey);
        return files != null && files.contains(classPath);
    }
}