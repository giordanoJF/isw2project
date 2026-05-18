package com.isw2project.metrics.impl;

import com.isw2project.gitextractor.GitCommandException;
import com.isw2project.metrics.ClassMetricInput;
import com.isw2project.metrics.Metric;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts PMD rule violations (code smells) for a batch of source files in a single PMD invocation.
 * This avoids paying the PMD initialization cost once per snapshot (which would be approximately 15000 times).
 * Instead, all files in a release are analyzed together, paying the init cost once per release.
 *
 * Usage: call analyzeBatch() once per release with all file paths, then compute() reads from cache.
 * The number of threads is auto-detected from available processors.
 */
public class CodeSmellsMetric implements Metric {

    private static final Logger log = LoggerFactory.getLogger(CodeSmellsMetric.class);

    // --- Tuning parameters ---
    // Number of files analyzed per PMD invocation.
    // Lower values reduce peak RAM usage; higher values reduce PMD init overhead.
    // Recommended range: 50-200.
    private static final int BATCH_SIZE = 100;

    // Fraction of available CPU cores assigned to PMD threads (e.g. 0.5 = half the cores).
    // Lower values leave more resources to the OS; higher values speed up analysis.
    // Recommended range: 0.25 - 0.75.
    private static final double CPU_FRACTION = 0.5;

    // PMD rulesets to apply. Remove or comment out categories to reduce analysis scope.
    private static final String[] RULESETS = {
            "category/java/bestpractices.xml",
            "category/java/design.xml",
            "category/java/errorprone.xml",
            "category/java/codestyle.xml"
    };
    // -------------------------

    // Cache of absolute file path -> violation count, populated by analyzeBatch().
    private final Map<String, Integer> violationCache = new HashMap<>();

    /**
     * Analyzes all given source files in sub-batches of BATCH_SIZE to limit peak memory usage.
     * Each sub-batch runs a separate PMD invocation, keeping memory pressure bounded.
     * Must be called before compute() for the files in this batch.
     */
    public void analyzeBatch(List<Path> sourceFiles) {
        if (sourceFiles.isEmpty()) return;
        for (int i = 0; i < sourceFiles.size(); i += BATCH_SIZE) {
            List<Path> subBatch = sourceFiles.subList(i, Math.min(i + BATCH_SIZE, sourceFiles.size()));
            analyzeSubBatch(subBatch);
        }
    }

    private void analyzeSubBatch(List<Path> sourceFiles) {

        PMDConfiguration config = new PMDConfiguration();
        config.setDefaultLanguageVersion(
                net.sourceforge.pmd.lang.LanguageRegistry.PMD
                        .getLanguageById("java")
                        .getDefaultVersion()
        );
        for (String ruleset : RULESETS) {
            config.addRuleSet(ruleset);
        }
        sourceFiles.forEach(config::addInputPath);
        config.setReportFormat("empty");
        config.setAnalysisCacheLocation(null);
        int threads = Math.max(1, (int) (Runtime.getRuntime().availableProcessors() * CPU_FRACTION));
        config.setThreads(threads);

        try (PmdAnalysis pmd = PmdAnalysis.create(config)) {
            Report report = pmd.performAnalysisAndCollectReport();
            // Initialize all files to 0 so files with no violations are still cached.
            sourceFiles.forEach(p -> violationCache.put(p.toAbsolutePath().toString(), 0));
            for (RuleViolation violation : report.getViolations()) {
                String filePath = violation.getFileId().getAbsolutePath();
                violationCache.merge(filePath, 1, Integer::sum);
            }
        } catch (Exception e) {
            log.warn("PMD batch analysis failed: {}", e.getMessage());
            sourceFiles.forEach(p -> violationCache.put(p.toAbsolutePath().toString(), 0));
        }
    }

    /**
     * Returns the cached violation count for the given file as a String.
     * Returns "0" if the file was not included in any analyzed batch.
     * Used by MetricsOrchestrator to read the actual current smells before shifting.
     */
    public String getCachedSmells(Path sourceFile) {
        String key = sourceFile.toAbsolutePath().toString();
        return String.valueOf(violationCache.getOrDefault(key, 0));
    }

    @Override
    public String columnName() { return "Previous_Release_Code_Smells"; }

    /**
     * Returns the violation count for this snapshot from the cache populated by analyzeBatch().
     * Falls back to 0 if the file was not included in any batch.
     */
    @Override
    public String compute(ClassMetricInput input) throws GitCommandException {
        String key = input.getSnapshot().getCode().toAbsolutePath().toString();
        return String.valueOf(violationCache.getOrDefault(key, 0));
    }
}