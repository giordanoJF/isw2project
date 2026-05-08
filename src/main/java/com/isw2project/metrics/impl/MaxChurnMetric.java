package com.isw2project.metrics.impl;

import com.isw2project.gitextractor.GitCommandException;
import com.isw2project.metrics.ClassMetricInput;
import com.isw2project.gitextractor.support.GitLogStatsService;

import com.isw2project.metrics.Metric;

import java.util.List;

/** Maximum churn (added + deleted) in a single revision up to the release. */
public class MaxChurnMetric implements Metric {

    private final GitLogStatsService gitLogStatsService;

    public MaxChurnMetric(GitLogStatsService gitLogStatsService) {
        this.gitLogStatsService = gitLogStatsService;
    }

    @Override
    public String columnName() { return "Max_Churn"; }

    @Override
    public String compute(ClassMetricInput input) throws GitCommandException {
        List<GitLogStatsService.CommitStat> stats =
                gitLogStatsService.getCommitStats(input.getSnapshot().getClassPath(), input.getGitRef());
        int max = stats.stream().mapToInt(GitLogStatsService.CommitStat::getChurn).max().orElse(0);
        return String.valueOf(max);
    }
}