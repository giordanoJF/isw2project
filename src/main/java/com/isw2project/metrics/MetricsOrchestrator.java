package com.isw2project.metrics;

import com.isw2project.metrics.impl.CodeSmellsMetric;
import com.isw2project.model.JavaClassSnapshot;
import com.isw2project.model.ReleaseSnapshot;
import com.isw2project.model.Version;
import com.isw2project.model.Issue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.isw2project.gitextractor.support.GitLogStatsService;
import com.isw2project.metrics.impl.*;

/**
 * Orchestrates metric computation across all snapshots.
 * CodeSmellsMetric is treated specially: files are analyzed in batch per release
 * to avoid paying the PMD initialization cost once per snapshot.
 * All other metrics read from the in-memory git log index built once at startup.
 */
public class MetricsOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MetricsOrchestrator.class);

    private final MetricsComputerService computerService;
    private final CodeSmellsMetric codeSmellsMetric;
    private final Map<String, String> jiraNameToGitRef;
    private final Map<String, LocalDate> jiraNameToReleaseDate;

    public MetricsOrchestrator(File repoDir,
                               List<Version> versions,
                               Map<String, String> jiraNameToGitRef,
                               List<Issue> issues,
                               Map<String, Set<String>> issueToFilesIndex) {
        this.jiraNameToGitRef = jiraNameToGitRef;
        this.jiraNameToReleaseDate = buildReleaseDateMap(versions);

        GitLogStatsService gitLogStats = new GitLogStatsService(repoDir);
        this.codeSmellsMetric = new CodeSmellsMetric();

        List<Metric> metrics = List.of(
                new SizeMetric(),
                new LOCTouchedMetric(gitLogStats),
                new NrMetric(gitLogStats),
                new NfixMetric(issues, issueToFilesIndex),
                new NauthMetric(gitLogStats),
                new LOCAddedMetric(gitLogStats),
                new MaxLOCAddedMetric(gitLogStats),
                new AvgLOCAddedMetric(gitLogStats),
                new ChurnMetric(gitLogStats),
                new MaxChurnMetric(gitLogStats),
                new AvgChurnMetric(gitLogStats),
                new ChangeSetSizeMetric(gitLogStats),
                new MaxChangeSetMetric(gitLogStats),
                new AvgChangeSetMetric(gitLogStats),
                new AgeMetric(gitLogStats),
                new WeightedAgeMetric(gitLogStats),
                new ExpMetric(gitLogStats),
                new SexpMetric(gitLogStats),
                codeSmellsMetric
        );

        this.computerService = new MetricsComputerService(metrics);
    }

    /**
     * Computes metrics for the given percentage of snapshots (0.0 to 1.0).
     * For each release, PMD analysis is run once in batch before iterating individual snapshots.
     *
     * Code_Smells are shifted by one release: the value assigned to (class, release_N) is the
     * count computed for (class, release_N-1). Snapshots in the first release get 0 code smells.
     * This reflects the fact that smells introduced in release N only affect release N+1 onwards.
     */
    public void computeAll(List<ReleaseSnapshot> snapshots, double percentage) {
        int total = snapshots.stream().mapToInt(r -> r.getClasses().size()).sum();
        int limit = (int) Math.ceil(total * Math.clamp(percentage, 0.0, 1.0));
        int processed = 0;
        long batchPmdMs = 0;

        // Code_Smells are shifted by one release: smells computed for release N are assigned to release N+1.
        // Snapshots in the first release get "0". Requires strict chronological iteration.
        Map<String, String> previousReleaseSmells = new LinkedHashMap<>();

        for (ReleaseSnapshot release : sortByReleaseDate(snapshots)) {
            if (processed >= limit) break;

            long batchStart = System.nanoTime();
            codeSmellsMetric.analyzeBatch(release.getClasses().stream().map(JavaClassSnapshot::getCode).toList());
            batchPmdMs += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - batchStart);

            // Collect smells for ALL classes before the limit check so the next release's
            // previousReleaseSmells map is always complete even when limit cuts mid-release.
            Map<String, String> currentReleaseSmells = collectSmellsByClassPath(release);

            String gitRef = jiraNameToGitRef.getOrDefault(release.getRelease(), release.getRelease());
            LocalDate releaseDate = jiraNameToReleaseDate.get(release.getRelease());

            for (JavaClassSnapshot snapshot : release.getClasses()) {
                if (processed >= limit) break;
                Map<String, String> metrics = computerService.compute(new ClassMetricInput(snapshot, gitRef, releaseDate));
                metrics.put(codeSmellsMetric.columnName(), previousReleaseSmells.getOrDefault(snapshot.getClassPath(), "0"));
                snapshot.setMetrics(metrics);
                processed++;
            }

            previousReleaseSmells = currentReleaseSmells;
        }

        log.info("Metrics computed for {}/{} snapshots ({}%).", processed, total, Math.round(percentage * 100));
        log.info("PMD batch analysis total time: {}ms.", batchPmdMs);
        computerService.logTimingSummary();
    }

    private List<ReleaseSnapshot> sortByReleaseDate(List<ReleaseSnapshot> snapshots) {
        return snapshots.stream()
                .sorted(Comparator.comparing(r -> jiraNameToReleaseDate.getOrDefault(r.getRelease(), LocalDate.MAX)))
                .toList();
    }

    private Map<String, String> collectSmellsByClassPath(ReleaseSnapshot release) {
        Map<String, String> smells = new LinkedHashMap<>();
        for (JavaClassSnapshot snapshot : release.getClasses()) {
            smells.put(snapshot.getClassPath(), codeSmellsMetric.getCachedSmells(snapshot.getCode()));
        }
        return smells;
    }

    private Map<String, LocalDate> buildReleaseDateMap(List<Version> versions) {
        Map<String, LocalDate> map = new LinkedHashMap<>();
        for (Version v : versions) {
            if (v.getReleaseDate() != null && !v.getReleaseDate().isBlank()) {
                map.put(v.getName(), LocalDate.parse(v.getReleaseDate()));
            }
        }
        return map;
    }
}