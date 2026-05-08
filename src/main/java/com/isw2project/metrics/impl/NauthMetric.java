package com.isw2project.metrics.impl;

import com.isw2project.gitextractor.GitCommandException;
import com.isw2project.metrics.ClassMetricInput;
import com.isw2project.gitextractor.support.GitLogStatsService;

import com.isw2project.metrics.Metric;

import java.util.List;

/** Number of distinct authors who committed to this file up to the release. */
public class NauthMetric implements Metric {

    private final GitLogStatsService gitLogStatsService;

    public NauthMetric(GitLogStatsService gitLogStatsService) {
        this.gitLogStatsService = gitLogStatsService;
    }

    @Override
    public String columnName() { return "Nauth"; }

    @Override
    public String compute(ClassMetricInput input) throws GitCommandException {
        List<GitLogStatsService.CommitStat> stats =
                gitLogStatsService.getCommitStats(input.getSnapshot().getClassPath(), input.getGitRef());
        long distinct = stats.stream().map(GitLogStatsService.CommitStat::getAuthorEmail).distinct().count();
        return String.valueOf(distinct);
    }
}