package com.isw2project.classifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Produces a rough wall-clock estimate for the Milestone 2 evaluation run
 * before any classifier is actually trained.
 *
 * CALIBRATION HISTORY:
 *
 * v1 — original estimates from config.yaml comments (reference hardware unknown):
 *   RF=60s, IBk=15s, NaiveBayes=5s; OVERSAMPLING x1.1; UNDERSAMPLING x1.0
 *
 * v2 — calibrated from a measured run (OpenJPA, 15k instances,
 *   19 features, 10x10 CV, 4 combination threads, rfSlots=1):
 *
 *   Classifier base times (NONE FS, NONE balancing):
 *     RF=757s  (measured: [1/27] RF|No|No: 21:17:18->21:29:55)
 *     IBk=227s (measured: [19/27] IBk|No|No: 21:40:14->21:44:01)
 *     NB=6s    (measured: [10/27] NB|No|No: 21:38:12->21:38:18)
 *
 *   Balancing multipliers (vs NONE baseline):
 *     UNDERSAMPLING x0.20  — shrinks training to approximately 2.7k instances (from 15k);
 *                            RF: 757->151s (x0.20), IBk: 227->37s (x0.16)
 *     OVERSAMPLING  x1.65  — grows training to approximately 27k instances (from 15k);
 *                            RF: 757->1254s (x1.66), IBk: 227->331s (x1.46)
 *     SMOTE         x2.0   — similar to OVERSAMPLING but adds k-NN overhead;
 *                            no direct measurement, estimated from OVERSAMPLING
 *
 *   FS overhead: filter methods (InfoGain, Spearman) are negligible for RF and IBk
 *   (within +-5% of base); add approximately 12s for NaiveBayes where FS computation is visible
 *   relative to its fast base time.
 *
 *   Wrapper FS (ForwardSearch, BackwardSearch): +3600s lower bound (unchanged;
 *   no wrapper run was performed to calibrate this value).
 *
 * NOTE: These baselines are hardware-specific. On a different machine (more cores,
 * faster RAM, rfSlots > 1) the RF and IBk times will be proportionally lower.
 * The balancing MULTIPLIERS (x0.20 / x1.65) are more stable across hardware
 * because they reflect the relative change in dataset size, not absolute speed.
 *
 * Wall-clock estimate = sum(per-combination seconds) / combinationThreads
 * Assumes perfect linear scaling (optimistic but sufficient for a pre-run estimate).
 */
public class RuntimeEstimatorService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeEstimatorService.class);

    // Base seconds per combination with NONE FS and NONE balancing
    // Source: measured run (see calibration history above)
    private static final int BASE_RF  = 757;
    private static final int BASE_IBK = 227;
    private static final int BASE_NB  =   6;

    // FS overhead in seconds added to NaiveBayes combinations only
    // (RF and IBk show <5% FS overhead — negligible)
    private static final int NB_FILTER_FS_OVERHEAD_S = 12;

    // FS overhead for wrapper methods (source: config.yaml "estimated 1-3 hours")
    private static final int WRAPPER_OVERHEAD_S = 3600; // lower bound

    // Balancing multipliers (source: calibrated from actual run — see header)
    private static final double UNDERSAMPLING_MULT = 0.20; // 5x speedup
    private static final double OVERSAMPLING_MULT  = 1.65;
    private static final double SMOTE_MULT         = 2.00; // estimated, not measured

    public void logEstimate(List<ClassifierType> classifiers,
                            List<FeatureSelectionStrategy> fsList,
                            List<BalancingStrategy> balList,
                            int combinationThreads) {
        long totalSeconds = 0;
        for (ClassifierType type : classifiers) {
            for (FeatureSelectionStrategy fs : fsList) {
                for (BalancingStrategy bal : balList) {
                    totalSeconds += estimateCombination(type, fs, bal);
                }
            }
        }

        long wallSeconds = Math.max(1, totalSeconds / combinationThreads);

        if (log.isInfoEnabled()) {
            log.info("Runtime estimate: approximately {} sequential -> approximately {} with {} thread(s)  [basato su misurazione reale]",
                    formatDuration(totalSeconds),
                    formatDuration(wallSeconds),
                    combinationThreads);
        }
    }

    private long estimateCombination(ClassifierType type,
                                     FeatureSelectionStrategy fs,
                                     BalancingStrategy bal) {
        int base = switch (type) {
            case RANDOM_FOREST -> BASE_RF;
            case IBK           -> BASE_IBK;
            case NAIVE_BAYES   -> BASE_NB;
        };

        int fsOverhead = switch (fs) {
            case FORWARD_SEARCH, BACKWARD_SEARCH -> WRAPPER_OVERHEAD_S;
            case INFO_GAIN, SPEARMAN -> type == ClassifierType.NAIVE_BAYES
                    ? NB_FILTER_FS_OVERHEAD_S : 0;
            default -> 0;
        };

        double balMult = switch (bal) {
            case UNDERSAMPLING -> UNDERSAMPLING_MULT;
            case OVERSAMPLING  -> OVERSAMPLING_MULT;
            case SMOTE         -> SMOTE_MULT;
            default            -> 1.0;
        };

        return Math.round((base + fsOverhead) * balMult);
    }

    private String formatDuration(long seconds) {
        if (seconds < 60)   return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return m > 0 ? h + "h " + m + "m" : h + "h";
    }
}