package com.isw2project.metrics.impl;

import com.isw2project.gitextractor.GitCommandException;
import com.isw2project.metrics.ClassMetricInput;
import com.isw2project.gitextractor.support.GitLogStatsService;

import com.isw2project.metrics.Metric;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Weighted age: age in days multiplied by LOC touched.
 */
public class WeightedAgeMetric implements Metric {

    private final GitLogStatsService gitLogStatsService;

    public WeightedAgeMetric(GitLogStatsService gitLogStatsService) {
        this.gitLogStatsService = gitLogStatsService;
    }

    @Override
    public String columnName() { return "Weighted_Age"; }

    @Override
    public String compute(ClassMetricInput input) throws GitCommandException {
        LocalDate firstDate = gitLogStatsService.getFirstCommitDate(
                input.getSnapshot().getClassPath(), input.getGitRef());
        if (firstDate == null) return "0";

        long days = ChronoUnit.DAYS.between(firstDate, input.getReleaseDate());
        List<GitLogStatsService.CommitStat> stats =
                gitLogStatsService.getCommitStats(input.getSnapshot().getClassPath(), input.getGitRef());
        int locTouched = stats.stream().mapToInt(GitLogStatsService.CommitStat::getLOCTouched).sum();
        return String.valueOf(Math.max(0, days) * locTouched);
    }
}