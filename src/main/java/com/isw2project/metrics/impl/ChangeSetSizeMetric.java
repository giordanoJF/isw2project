package com.isw2project.metrics.impl;

import com.isw2project.gitextractor.GitCommandException;
import com.isw2project.metrics.ClassMetricInput;
import com.isw2project.gitextractor.support.GitLogStatsService;

import com.isw2project.metrics.Metric;

import java.util.List;

/** Sum of files committed together with this file across all revisions up to the release. */
public class ChangeSetSizeMetric implements Metric {

    private final GitLogStatsService gitLogStatsService;

    public ChangeSetSizeMetric(GitLogStatsService gitLogStatsService) {
        this.gitLogStatsService = gitLogStatsService;
    }

    @Override
    public String columnName() { return "Change_Set_Size"; }

    @Override
    public String compute(ClassMetricInput input) throws GitCommandException {
        List<GitLogStatsService.CommitStat> stats =
                gitLogStatsService.getCommitStats(input.getSnapshot().getClassPath(), input.getGitRef());
        int total = stats.stream().mapToInt(GitLogStatsService.CommitStat::getChangeSetSize).sum();
        return String.valueOf(total);
    }
}