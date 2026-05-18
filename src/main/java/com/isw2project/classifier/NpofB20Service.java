package com.isw2project.classifier;

import weka.classifiers.evaluation.NominalPrediction;
import weka.classifiers.evaluation.Prediction;
import weka.classifiers.Evaluation;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes NPofB20 (Normalized Proportion of Bugs found in the top 20% of code).
 *
 * WHY A SEPARATE CLASS:
 * The other four metrics (Precision, Recall, AUC, Kappa) are built into Weka's
 * Evaluation class and are retrieved with a single getter after crossValidateModel.
 * NPofB20 is not provided by Weka: it requires accessing the raw per-instance
 * predictions (Evaluation.predictions()), sorting them by predicted probability,
 * and performing a custom computation. That logic does not belong in
 * ClassifierEvaluatorService, which is responsible for running CV — not for
 * computing custom metrics. Isolating it here keeps both classes focused on a
 * single responsibility.
 *
 * WHAT IT MEASURES:
 * Instances are ranked by descending predicted probability of being buggy.
 * NPofB20 asks: "if a tester inspects only the top 20% of files by risk score,
 * what fraction of all real bugs will they find?" It translates model quality
 * into a concrete resource-allocation answer, unlike AUC which measures ranking
 * quality across all thresholds without fixing an inspection budget.
 *
 * Formula:
 *
 *   NPofB20 = |bugs in top-20%| / |total bugs|
 *
 * where "top-20%" is the set of ceil(N * 0.20) instances with the highest
 * predicted probability of being buggy (N = total instances in the fold set).
 *
 * Interpretation:
 *   1.0   -> all bugs are concentrated in the top 20%: perfect prioritization
 *   0.20  -> classifier is no better than random inspection
 *   ~0.09 -> worse than random (bug prevalence rate, classifier inverted)
 *
 * Predictions are accumulated across all folds by Weka's crossValidateModel,
 * so this method operates on the complete cross-validated prediction set.
 */
public class NpofB20Service {

    public double compute(Evaluation eval, int posClassIndex) {
        List<double[]> predictions = collectPredictions(eval, posClassIndex);
        if (predictions.isEmpty()) {
            return 0.0;
        }

        predictions.sort((a, b) -> Double.compare(b[0], a[0]));

        int total = predictions.size();
        int top20Count = (int) Math.ceil(total * 0.20);
        long totalBugs = predictions.stream().filter(p -> p[1] == posClassIndex).count();

        if (totalBugs == 0) {
            return 0.0;
        }

        long bugsInTop20 = predictions.subList(0, top20Count).stream()
                .filter(p -> p[1] == posClassIndex)
                .count();

        return (double) bugsInTop20 / totalBugs;
    }

    private List<double[]> collectPredictions(Evaluation eval, int posClassIndex) {
        List<double[]> result = new ArrayList<>();
        for (Prediction pred : eval.predictions()) {
            NominalPrediction np = (NominalPrediction) pred;
            double prob   = np.distribution()[posClassIndex];
            double actual = np.actual();
            result.add(new double[]{prob, actual});
        }
        return result;
    }
}
