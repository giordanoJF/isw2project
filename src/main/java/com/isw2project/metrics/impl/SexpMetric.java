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
 * Average developer experience in the same subsystem (top-level directory of the file path)
 * at the time of each commit to this file, averaged across all commits.
 * Aggregated to file-release level as the mean across all commits touching the file.
 */
public class SexpMetric implements Metric {

    private final GitLogStatsService gitLogStats;
    // subsystem -> author -> sorted commit dates
    private final Map<String, Map<String, List<LocalDate>>> subsystemAuthorDates;

    public SexpMetric(GitLogStatsService gitLogStats) {
        this.gitLogStats = gitLogStats;
        this.subsystemAuthorDates = buildSubsystemAuthorDates(gitLogStats.getFullIndex());
    }

    @Override
    public String columnName() { return "SEXP"; }

    @Override
    public String compute(ClassMetricInput input) throws GitCommandException {
        String filePath = input.getSnapshot().getClassPath();
        String sub = subsystem(filePath);
        List<CommitStat> stats = gitLogStats.getCommitStats(filePath, input.getGitRef());
        if (stats.isEmpty()) return "0";
        Map<String, List<LocalDate>> authorDates = subsystemAuthorDates.getOrDefault(sub, Collections.emptyMap());
        double avg = stats.stream()
                .mapToInt(cs -> countDatesUpTo(authorDates.getOrDefault(cs.getAuthorEmail(), Collections.emptyList()), cs.getDate()))
                .average()
                .orElse(0);
        return String.valueOf((int) Math.round(avg));
    }

    private static Map<String, Map<String, List<LocalDate>>> buildSubsystemAuthorDates(
            Map<String, List<CommitStat>> fullIndex) {
        Map<String, Map<String, TreeSet<LocalDate>>> sets = new HashMap<>();
        for (Map.Entry<String, List<CommitStat>> entry : fullIndex.entrySet()) {
            String sub = subsystem(entry.getKey());
            Map<String, TreeSet<LocalDate>> authorSets = sets.computeIfAbsent(sub, k -> new HashMap<>());
            for (CommitStat cs : entry.getValue()) {
                authorSets.computeIfAbsent(cs.getAuthorEmail(), k -> new TreeSet<>()).add(cs.getDate());
            }
        }
        Map<String, Map<String, List<LocalDate>>> result = new HashMap<>();
        sets.forEach((sub, authorSets) -> {
            Map<String, List<LocalDate>> authorLists = new HashMap<>();
            authorSets.forEach((author, dates) -> authorLists.put(author, new ArrayList<>(dates)));
            result.put(sub, authorLists);
        });
        return result;
    }

    private static String subsystem(String filePath) {
        int idx = filePath.indexOf('/');
        return idx < 0 ? filePath : filePath.substring(0, idx);
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