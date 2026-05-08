package com.isw2project.metrics;

import com.isw2project.gitextractor.GitCommandException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Applies all registered Metric implementations to a single JavaClassSnapshot.
 * Tracks cumulative execution time per metric to help identify bottlenecks.
 * Errors on individual metrics are logged and reported as empty string.
 */
public class MetricsComputerService {

    private static final Logger log = LoggerFactory.getLogger(MetricsComputerService.class);

    private final List<Metric> metrics;
    private final Map<String, Long> cumulativeTimeMs;
    private int snapshotsProcessed;

    public MetricsComputerService(List<Metric> metrics) {
        this.metrics = metrics;
        this.cumulativeTimeMs = new LinkedHashMap<>();
        this.snapshotsProcessed = 0;
        metrics.forEach(m -> cumulativeTimeMs.put(m.columnName(), 0L));
    }

    /**
     * Computes all metrics for the given input and returns a column-name -> value map.
     */
    public Map<String, String> compute(ClassMetricInput input) {
        Map<String, String> result = new LinkedHashMap<>();
        snapshotsProcessed++;

        for (Metric metric : metrics) {
            long start = System.nanoTime();
            try {
                result.put(metric.columnName(), metric.compute(input));
            } catch (GitCommandException e) {
                log.warn("Metric '{}' failed for '{}' at '{}': {}",
                        metric.columnName(),
                        input.getSnapshot().getClassPath(),
                        input.getSnapshot().getRelease(),
                        e.getMessage());
                result.put(metric.columnName(), "");
            } finally {
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                cumulativeTimeMs.merge(metric.columnName(), elapsedMs, Long::sum);
            }
        }

        return result;
    }

    /**
     * Logs a summary of cumulative time spent per metric.
     * Call this after computeAll() to identify bottlenecks.
     */
    public void logTimingSummary() {
        log.info("--- Metric timing summary ({} snapshots processed) ---", snapshotsProcessed);
        cumulativeTimeMs.forEach((name, totalMs) ->
                log.info("  {}: {}ms total, avg {}ms/snapshot",
                        name, totalMs, snapshotsProcessed > 0 ? totalMs / snapshotsProcessed : 0));
    }
}