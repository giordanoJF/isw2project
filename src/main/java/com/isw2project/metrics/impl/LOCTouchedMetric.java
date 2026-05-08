package com.isw2project.metrics.impl;

import com.isw2project.gitextractor.GitCommandException;
import com.isw2project.metrics.ClassMetricInput;
import com.isw2project.gitextractor.support.GitLogStatsService;
import com.isw2project.metrics.Metric;

import java.util.List;

/** Sum of lines added and deleted across all revisions up to the release. */
public class LOCTouchedMetric implements Metric {

    private final GitLogStatsService gitLogStatsService;

    public LOCTouchedMetric(GitLogStatsService gitLogStatsService) {
        this.gitLogStatsService = gitLogStatsService;
    }

    @Override
    public String columnName() { return "LOC_Touched"; }

    @Override
    public String compute(ClassMetricInput input) throws GitCommandException {
        List<GitLogStatsService.CommitStat> stats =
                gitLogStatsService.getCommitStats(input.getSnapshot().getClassPath(), input.getGitRef());
        int total = stats.stream().mapToInt(GitLogStatsService.CommitStat::getLOCTouched).sum();
        return String.valueOf(total);
    }
}