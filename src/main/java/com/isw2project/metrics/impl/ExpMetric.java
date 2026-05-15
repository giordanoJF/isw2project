package com.isw2project.metrics.impl;

import com.isw2project.gitextractor.GitCommandException;
import com.isw2project.gitextractor.support.GitLogStatsService;
import com.isw2project.gitextractor.support.GitLogStatsService.CommitStat;
import com.isw2project.metrics.ClassMetricInput;
import com.isw2project.metrics.Metric;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Average developer experience at the time of each commit to this file, averaged across all commits.
 * Experience = total distinct commit-days the author has in the entire repository up to that point.
 * Aggregated to file-release level as the mean across all commits touching the file.
 */
public class ExpMetric implements Metric {

    private final GitLogStatsService gitLogStats;
    private final Map<String, List<LocalDate>> authorDates;

    public ExpMetric(GitLogStatsService gitLogStats) {
        this.gitLogStats = gitLogStats;
        this.authorDates = buildAuthorDates(gitLogStats.getFullIndex());
    }

    @Override
    public String columnName() { return "EXP"; }

    @Override
    public String compute(ClassMetricInput input) throws GitCommandException {
        List<CommitStat> stats = gitLogStats.getCommitStats(
                input.getSnapshot().getClassPath(), input.getGitRef());
        if (stats.isEmpty()) return "0";
        double avg = stats.stream()
                .mapToInt(cs -> countDatesUpTo(authorDates.getOrDefault(cs.getAuthorEmail(), Collections.emptyList()), cs.getDate()))
                .average()
                .orElse(0);
        return String.valueOf((int) Math.round(avg));
    }

    private static Map<String, List<LocalDate>> buildAuthorDates(Map<String, List<CommitStat>> fullIndex) {
        Map<String, TreeSet<LocalDate>> sets = new HashMap<>();
        for (List<CommitStat> commits : fullIndex.values()) {
            for (CommitStat cs : commits) {
                sets.computeIfAbsent(cs.getAuthorEmail(), k -> new TreeSet<>()).add(cs.getDate());
            }
        }
        Map<String, List<LocalDate>> result = new HashMap<>();
        sets.forEach((author, dates) -> result.put(author, new ArrayList<>(dates)));
        return result;
    }

    private static int countDatesUpTo(List<LocalDate> sortedDates, LocalDate upTo) {
        int lo = 0;
        int hi = sortedDates.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (!sortedDates.get(mid).isAfter(upTo)) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }
}