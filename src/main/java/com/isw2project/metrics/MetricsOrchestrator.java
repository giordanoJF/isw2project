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
     *
     * Code_Smells are shifted by one release: the value assigned to (class, release_N) is the
     * count computed for (class, release_N-1). Snapshots in the first release get 0 code smells.
     * This reflects the fact that smells introduced in release N only affect release N+1 onwards.
     */
    public void computeAll(List<ReleaseSnapshot> snapshots, double percentage) {
        int total = snapshots.stream().mapToInt(r -> r.getClasses().size()).sum();
        int limit = (int) Math.ceil(total * Math.min(1.0, Math.max(0.0, percentage)));
        int processed = 0;
        long batchPmdMs = 0;

        // Holds Code_Smells computed in the previous release, keyed by classPath.
        // Used to assign the shifted value to the current release.
        Map<String, String> previousReleaseSmells = new LinkedHashMap<>();

        for (ReleaseSnapshot release : snapshots) {
            if (processed >= limit) break;

            // Run PMD on the current release files to get their actual smells.
            // These will be used as the shifted value for the NEXT release.
            List<Path> releaseFiles = release.getClasses().stream()
                    .map(JavaClassSnapshot::getCode)
                    .toList();
            long batchStart = System.nanoTime();
            codeSmellsMetric.analyzeBatch(releaseFiles);
            batchPmdMs += java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - batchStart);

            String gitRef = jiraNameToGitRef.getOrDefault(release.getRelease(), release.getRelease());
            LocalDate releaseDate = jiraNameToReleaseDate.get(release.getRelease());

            // Collect current release smells for ALL files in this release,
            // regardless of the processing limit. This ensures previousReleaseSmells
            // is always complete for the next release even when limit cuts mid-release.
            Map<String, String> currentReleaseSmells = new LinkedHashMap<>();
            for (JavaClassSnapshot snapshot : release.getClasses()) {
                currentReleaseSmells.put(snapshot.getClassPath(),
                        codeSmellsMetric.getCachedSmells(snapshot.getCode()));
            }

            for (JavaClassSnapshot snapshot : release.getClasses()) {
                if (processed >= limit) break;

                ClassMetricInput input = new ClassMetricInput(snapshot, gitRef, releaseDate);
                Map<String, String> metrics = computerService.compute(input);

                // Override Code_Smells with the value from the previous release (shift by one).
                // If the class did not exist in the previous release, default to "0".
                metrics.put(codeSmellsMetric.columnName(),
                        previousReleaseSmells.getOrDefault(snapshot.getClassPath(), "0"));


                snapshot.setMetrics(metrics);
                processed++;
            }

            previousReleaseSmells = currentReleaseSmells;
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