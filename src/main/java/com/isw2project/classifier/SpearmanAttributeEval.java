package com.isw2project.classifier;

import weka.attributeSelection.ASEvaluation;
import weka.attributeSelection.AttributeEvaluator;
import weka.core.Capabilities;
import weka.core.Capabilities.Capability;
import weka.core.Instances;

import java.util.Arrays;

/**
 * Weka AttributeEvaluator that scores each attribute by its absolute Spearman
 * rank correlation with the class attribute.
 *
 * Weka 3.8 stable does not include a Spearman-based AttributeEvaluator.
 * CorrelationAttributeEval (Pearson) is available, but Pearson assumes a linear
 * relationship and is sensitive to outliers - both problematic for skewed code
 * metrics such as LOC or churn. This class is an original implementation written
 * from scratch, based on the standard mathematical definition (Spearman, 1904).
 * No external source or reference implementation was used.
 *
 * Formula:
 *
 *   score(X) = |rho(X, C)|
 *
 * where C is the class attribute and rho is the Spearman rank correlation:
 *
 *   rho(X, C) = Pearson(rank(X), rank(C))
 *
 * Pearson correlation:
 *
 *   r(X, Y) = SUM_i[(xi - x_mean)(yi - y_mean)]
 *             / SQRT[ SUM_i(xi - x_mean)^2 * SUM_i(yi - y_mean)^2 ]
 *
 * Ranking (with average ranks for ties):
 *   Sort the values; for tied values occupying sorted positions i..j (0-based),
 *   assign rank = (i + j) / 2 + 1 to all of them.
 *   Example: values [3, 1, 3] -> sorted positions: 1->pos0, 3->pos1, 3->pos2
 *            ranks: 1 gets rank 1; the two 3s get rank (1+2)/2+1 = 2.5
 */
public class SpearmanAttributeEval extends ASEvaluation implements AttributeEvaluator {

    private static final long serialVersionUID = 1L;

    private Instances data;

    @Override
    public void buildEvaluator(Instances data) {
        this.data = data;
    }

    @Override
    public double evaluateAttribute(int attribute) {
        int n = data.numInstances();
        double[] attrVals  = new double[n];
        double[] classVals = new double[n];
        for (int i = 0; i < n; i++) {
            attrVals[i]  = data.instance(i).value(attribute);
            classVals[i] = data.instance(i).classValue();
        }
        return Math.abs(pearson(rank(attrVals), rank(classVals)));
    }

    @Override
    public Capabilities getCapabilities() {
        Capabilities caps = super.getCapabilities();
        caps.disableAll();
        caps.enableAllAttributes();
        caps.enable(Capability.MISSING_VALUES);
        caps.enable(Capability.NOMINAL_CLASS);
        caps.enable(Capability.NUMERIC_CLASS);
        caps.enable(Capability.MISSING_CLASS_VALUES);
        return caps;
    }

    /**
     * Assigns 1-based average ranks to the values, handling ties correctly.
     */
    private double[] rank(double[] values) {
        int n = values.length;
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) {
            indices[i] = i;
        }
        Arrays.sort(indices, (a, b) -> Double.compare(values[a], values[b]));

        double[] ranks = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j < n - 1 && values[indices[j]] == values[indices[j + 1]]) {
                j++;
            }
            double avgRank = (i + j) / 2.0 + 1.0;
            for (int k = i; k <= j; k++) {
                ranks[indices[k]] = avgRank;
            }
            i = j + 1;
        }
        return ranks;
    }

    private double pearson(double[] x, double[] y) {
        int n = x.length;
        double sumX = 0;
        double sumY = 0;
        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
        }
        double meanX = sumX / n;
        double meanY = sumY / n;

        double num = 0;
        double denX = 0;
        double denY = 0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            num  += dx * dy;
            denX += dx * dx;
            denY += dy * dy;
        }
        double den = Math.sqrt(denX * denY);
        return den == 0.0 ? 0.0 : num / den;
    }

    @Override
    public String toString() {
        return "SpearmanAttributeEval";
    }
}
