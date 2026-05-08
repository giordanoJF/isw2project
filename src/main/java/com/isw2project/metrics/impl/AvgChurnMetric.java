package com.isw2project.metrics.impl;

import com.isw2project.gitextractor.GitCommandException;
import com.isw2project.metrics.ClassMetricInput;
import com.isw2project.gitextractor.support.GitLogStatsService;

import com.isw2project.metrics.Metric;

import java.util.List;

/** Average churn per revision up to the release. */
public class AvgChurnMetric implements Metric {

    private final GitLogStatsService gitLogStatsService;

    public AvgChurnMetric(GitLogStatsService gitLogStatsService) {
        this.gitLogStatsService = gitLogStatsService;
    }

    @Override
    public String columnName() { return "Avg_Churn"; }

    @Override
    public String compute(ClassMetricInput input) throws GitCommandException {
        List<GitLogStatsService.CommitStat> stats =
                gitLogStatsService.getCommitStats(input.getSnapshot().getClassPath(), input.getGitRef());
        if (stats.isEmpty()) return "0";
        double avg = stats.stream().mapToInt(GitLogStatsService.CommitStat::getChurn).average().orElse(0);
        return String.format("%.2f", avg);
    }
}