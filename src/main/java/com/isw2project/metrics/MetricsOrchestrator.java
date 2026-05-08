package com.isw2project.metrics;

import com.isw2project.metrics.impl.CodeSmellsMetric;
import com.isw2project.model.JavaClassSnapshot;
import com.isw2project.model.ReleaseSnapshot;
import com.isw2project.model.Version;
import com.isw2project.model.Issue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                codeSmellsMetric
        );

        this.computerService = new MetricsComputerService(metrics);
    }

    /**
     * Computes metrics for the given percentage of snapshots (0.0 to 1.0).
     * For each release, PMD analysis is run once in batch before iterating individual snapshots.
     */
    public void computeAll(List<ReleaseSnapshot> snapshots, double percentage) {
        int total = snapshots.stream().mapToInt(r -> r.getClasses().size()).sum();
        int limit = (int) Math.ceil(total * Math.min(1.0, Math.max(0.0, percentage)));
        int processed = 0;
        long batchPmdMs = 0;

        for (ReleaseSnapshot release : snapshots) {
            if (processed >= limit) break;

            // Run PMD once for all files in this release before computing per-snapshot metrics.
            // Timing is tracked separately since it happens outside the per-snapshot compute loop.
            List<Path> releaseFiles = release.getClasses().stream()
                    .map(s -> s.getCode())
                    .toList();
            long batchStart = System.nanoTime();
            codeSmellsMetric.analyzeBatch(releaseFiles);
            batchPmdMs += java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - batchStart);

            String gitRef = jiraNameToGitRef.getOrDefault(release.getRelease(), release.getRelease());
            LocalDate releaseDate = jiraNameToReleaseDate.get(release.getRelease());

            for (JavaClassSnapshot snapshot : release.getClasses()) {
                if (processed >= limit) break;
                ClassMetricInput input = new ClassMetricInput(snapshot, gitRef, releaseDate);
                snapshot.setMetrics(computerService.compute(input));
                processed++;
            }
        }

        log.info("Metrics computed for {}/{} snapshots ({}%).", processed, total,
                Math.round(percentage * 100));
        log.info("PMD batch analysis total time: {}ms ({} releases analyzed).",
                batchPmdMs, snapshots.size());
        computerService.logTimingSummary();
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