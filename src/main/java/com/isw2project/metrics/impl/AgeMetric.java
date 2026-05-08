package com.isw2project.metrics.impl;

import com.isw2project.gitextractor.GitCommandException;
import com.isw2project.metrics.ClassMetricInput;
import com.isw2project.gitextractor.support.GitLogStatsService;

import com.isw2project.metrics.Metric;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Age of the file at the release: days from the file's first commit to the release date.
 */
public class AgeMetric implements Metric {

    private final GitLogStatsService gitLogStatsService;

    public AgeMetric(GitLogStatsService gitLogStatsService) {
        this.gitLogStatsService = gitLogStatsService;
    }

    @Override
    public String columnName() { return "Age"; }

    @Override
    public String compute(ClassMetricInput input) throws GitCommandException {
        LocalDate firstDate = gitLogStatsService.getFirstCommitDate(
                input.getSnapshot().getClassPath(), input.getGitRef());
        if (firstDate == null) return "0";
        long days = ChronoUnit.DAYS.between(firstDate, input.getReleaseDate());
        return String.valueOf(Math.max(0, days));
    }
}