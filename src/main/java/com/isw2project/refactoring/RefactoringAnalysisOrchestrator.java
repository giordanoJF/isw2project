package com.isw2project.refactoring;

import com.isw2project.config.RefactoringConfig;
import com.isw2project.csv.CsvWriterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class RefactoringAnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RefactoringAnalysisOrchestrator.class);

    private final RefactoredMetricsService metricsService;
    private final RefactoredPredictorService predictorService;
    private final BugginessCorrelationService correlationService;
    private final CsvWriterService csvWriter;

    public RefactoringAnalysisOrchestrator(RefactoredMetricsService metricsService,
                                            RefactoredPredictorService predictorService,
                                            BugginessCorrelationService correlationService,
                                            CsvWriterService csvWriter) {
        this.metricsService = metricsService;
        this.predictorService = predictorService;
        this.correlationService = correlationService;
        this.csvWriter = csvWriter;
    }

    public void run(RefactoringConfig config) {
        log.info("=== Refactoring Analysis (M4) ===");

        List<Map<String, String>> metricsRows = metricsService.compute();
        log.info("Computed metrics for {} class versions", metricsRows.size());

        try {
            predictorService.annotateWithPredictions(metricsRows, config.getInputCsv());
        } catch (Exception e) {
            log.error("Prediction failed: {}", e.getMessage(), e);
        }

        csvWriter.write(config.getOutputDir(), "refactored_metrics.csv", metricsRows);
        log.info("Written refactored_metrics.csv");

        try {
            List<Map<String, String>> correlationRows = correlationService.compute(config.getInputCsv());
            csvWriter.write(config.getOutputDir(), "feature_bugginess_correlation.csv", correlationRows);
            log.info("Written feature_bugginess_correlation.csv ({} features)", correlationRows.size());
        } catch (Exception e) {
            log.error("Correlation computation failed: {}", e.getMessage(), e);
        }

        log.info("=== Refactoring Analysis complete ===");
    }
}
