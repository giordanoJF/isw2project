package com.isw2project.classifier;

import com.isw2project.config.ClassifierConfig;
import com.isw2project.config.ParallelismConfig;
import com.isw2project.csv.CsvWriterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates the Milestone 2 classifier evaluation pipeline.
 *
 * Reads the Milestone 1 snapshot CSV, iterates over all enabled combinations of
 * classifier x feature-selection x balancing, runs 10-times 10-fold cross-validation
 * for each combination, and writes the results to a CSV.
 *
 * Combinations are evaluated in parallel on a thread pool whose size is driven by
 * config.parallelism.combinations (0 = all CPU cores). Each combination creates its
 * own Classifier and Evaluation objects; the shared Instances object is only read,
 * never written, so no synchronization is needed on the data itself.
 */
public class ClassifierOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ClassifierOrchestrator.class);

    private final ClassifierConfig config;
    private final WekaDatasetService datasetService;
    private final ClassifierBuilderService classifierBuilder;
    private final ClassifierEvaluatorService evaluatorService;
    private final ClassifierResultRowMapperService rowMapper;
    private final CsvWriterService csvWriter;
    private final RuntimeEstimatorService runtimeEstimator;

    public ClassifierOrchestrator(ClassifierConfig config,
                                  WekaDatasetService datasetService,
                                  ClassifierBuilderService classifierBuilder,
                                  ClassifierEvaluatorService evaluatorService,
                                  ClassifierResultRowMapperService rowMapper,
                                  CsvWriterService csvWriter) {
        this.config            = config;
        this.datasetService    = datasetService;
        this.classifierBuilder = classifierBuilder;
        this.evaluatorService  = evaluatorService;
        this.rowMapper         = rowMapper;
        this.csvWriter         = csvWriter;
        this.runtimeEstimator  = new RuntimeEstimatorService();
    }

    public void run() {
        log.info("=== Milestone 2 - Classifier Evaluation ===");

        Instances data;
        try {
            data = datasetService.load(config.getInputCsv());
        } catch (Exception e) {
            log.error("Failed to load dataset from {}: {}", config.getInputCsv(), e.getMessage());
            return;
        }

        int posClassIndex = datasetService.getPositiveClassIndex(data);
        log.info("Positive class index (buggy='true'): {}", posClassIndex);

        List<ClassifierType>           classifiers = parseClassifiers();
        List<FeatureSelectionStrategy> fsList      = parseFsStrategies();
        List<BalancingStrategy>        balList      = parseBalancingStrategies();

        List<Combination> combinations = buildCombinations(classifiers, fsList, balList);

        ParallelismConfig par     = config.getParallelism();
        int combinationThreads    = par.resolvedCombinationThreads();
        int rfSlots               = par.resolvedRandomForestSlots();

        log.info("Combinations: {} | Threads: {} | RF slots: {}",
                combinations.size(), combinationThreads, rfSlots);
        runtimeEstimator.logEstimate(classifiers, fsList, balList, combinationThreads);

        List<Map<String, String>> rows = combinationThreads > 1
                ? runParallel(combinations, data, posClassIndex, rfSlots, combinationThreads)
                : runSequential(combinations, data, posClassIndex, rfSlots);

        String filename = config.getDatasetName() + "_classifier.csv";
        csvWriter.write(config.getOutputDir(), filename, rows);
        log.info("Results written to {}/{}", config.getOutputDir(), filename);
        log.info("=== Milestone 2 complete ===");
    }

    private List<Map<String, String>> runSequential(List<Combination> combinations,
                                                     Instances data,
                                                     int posClassIndex,
                                                     int rfSlots) {
        List<Map<String, String>> rows = new ArrayList<>();
        int total = combinations.size();
        int done  = 0;
        for (Combination c : combinations) {
            done++;
            logProgress(done, total, c);
            Map<String, String> row = evaluate(c, data, posClassIndex, rfSlots);
            if (!row.isEmpty()) rows.add(row);
        }
        return rows;
    }

    private List<Map<String, String>> runParallel(List<Combination> combinations,
                                                   Instances data,
                                                   int posClassIndex,
                                                   int rfSlots,
                                                   int threads) {
        AtomicInteger done = new AtomicInteger(0);
        int total          = combinations.size();

        List<Future<Map<String, String>>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (Combination c : combinations) {
                futures.add(pool.submit(() -> {
                    logProgress(done.incrementAndGet(), total, c);
                    return evaluate(c, data, posClassIndex, rfSlots);
                }));
            }
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (Future<Map<String, String>> f : futures) {
            try {
                Map<String, String> row = f.get();
                if (!row.isEmpty()) rows.add(row);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Evaluation interrupted: {}", e.getMessage());
            } catch (ExecutionException e) {
                log.error("Evaluation failed in parallel thread: {}", e.getCause().getMessage());
            }
        }
        return rows;
    }

    private Map<String, String> evaluate(Combination c,
                                          Instances data,
                                          int posClassIndex,
                                          int rfSlots) {
        try {
            Classifier clf = classifierBuilder.build(c.type(), c.fs(), c.bal(), data, rfSlots);
            int runs  = config.getCrossValidation().getRuns();
            int folds = config.getCrossValidation().getFolds();
            double[] metrics = evaluatorService.evaluate(data, clf, runs, folds, posClassIndex);
            return rowMapper.toRow(config.getDatasetName(), c.type(), c.fs(), c.bal(), metrics);
        } catch (Exception e) {
            log.error("Evaluation failed for {} | FS={} | Bal={}: {}",
                    c.type().getDisplayName(), c.fs().getDisplayName(), c.bal().getDisplayName(),
                    e.getMessage());
            return Map.of();
        }
    }

    private void logProgress(int done, int total, Combination c) {
        log.info("[{}/{}] {} | FS={} | Bal={}",
                done, total,
                c.type().getDisplayName(), c.fs().getDisplayName(), c.bal().getDisplayName());
    }

    private List<Combination> buildCombinations(List<ClassifierType> classifiers,
                                                 List<FeatureSelectionStrategy> fsList,
                                                 List<BalancingStrategy> balList) {
        List<Combination> list = new ArrayList<>();
        for (ClassifierType type : classifiers) {
            for (FeatureSelectionStrategy fs : fsList) {
                for (BalancingStrategy bal : balList) {
                    list.add(new Combination(type, fs, bal));
                }
            }
        }
        return list;
    }

    private List<ClassifierType> parseClassifiers() {
        List<ClassifierType> result = new ArrayList<>();
        for (String s : config.getClassifiers()) {
            try {
                result.add(ClassifierType.valueOf(s));
            } catch (IllegalArgumentException _) {
                log.warn("Unknown classifier '{}' in config - skipped", s);
            }
        }
        return result;
    }

    private List<FeatureSelectionStrategy> parseFsStrategies() {
        List<FeatureSelectionStrategy> result = new ArrayList<>();
        for (String s : config.getFeatureSelection()) {
            try {
                result.add(FeatureSelectionStrategy.valueOf(s));
            } catch (IllegalArgumentException _) {
                log.warn("Unknown FS strategy '{}' in config - skipped", s);
            }
        }
        return result;
    }

    private List<BalancingStrategy> parseBalancingStrategies() {
        List<BalancingStrategy> result = new ArrayList<>();
        for (String s : config.getBalancing()) {
            try {
                result.add(BalancingStrategy.valueOf(s));
            } catch (IllegalArgumentException _) {
                log.warn("Unknown balancing strategy '{}' in config - skipped", s);
            }
        }
        return result;
    }

    private record Combination(ClassifierType type,
                               FeatureSelectionStrategy fs,
                               BalancingStrategy bal) {}
}
