package com.isw2project.classifier;

import weka.classifiers.evaluation.NominalPrediction;
import weka.classifiers.evaluation.Prediction;
import weka.classifiers.Evaluation;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes NPofB20 (Normalized Proportion of Bugs found in the top 20% of code).
 *
 * Not provided by Weka's Evaluation class, unlike Precision/Recall/AUC/Kappa.
 * Requires ranking instances by predicted bug probability and computing the fraction
 * of real bugs captured in the top 20% of ranked files: |bugs in top-20%| / |total bugs|.
 * Separated from ClassifierEvaluatorService to keep CV logic and metric computation distinct.
 *
 * A score of 0.20 means the classifier is no better than random; 1.0 is perfect.
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
