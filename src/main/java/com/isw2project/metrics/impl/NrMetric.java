package com.isw2project.metrics.impl;

import com.isw2project.gitextractor.GitCommandException;
import com.isw2project.metrics.ClassMetricInput;
import com.isw2project.gitextractor.support.GitLogStatsService;

import com.isw2project.metrics.Metric;

import java.util.List;

/** Number of revisions (commits) that touched this file up to the release. */
public class NrMetric implements Metric {

    private final GitLogStatsService gitLogStatsService;

    public NrMetric(GitLogStatsService gitLogStatsService) {
        this.gitLogStatsService = gitLogStatsService;
    }

    @Override
    public String columnName() { return "NR"; }

    @Override
    public String compute(ClassMetricInput input) throws GitCommandException {
        List<GitLogStatsService.CommitStat> stats =
                gitLogStatsService.getCommitStats(input.getSnapshot().getClassPath(), input.getGitRef());
        return String.valueOf(stats.size());
    }
}