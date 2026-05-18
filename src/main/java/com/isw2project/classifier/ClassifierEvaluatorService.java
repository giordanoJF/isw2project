package com.isw2project.classifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;

import java.util.Random;

/**
 * Runs 10-times 10-fold cross-validation and returns averaged performance metrics.
 *
 * Each of the 10 runs uses a different random seed passed to crossValidateModel,
 * which handles shuffling and stratification internally. The data object is never
 * modified: crossValidateModel creates its own copy, making this method safe to
 * call from multiple threads on the same Instances object.
 *
 * Metrics returned (indexed 0..4):
 *   0 - Precision  (positive class)
 *   1 - Recall     (positive class)
 *   2 - AUC        (area under ROC, positive class)
 *   3 - Kappa      (Cohen's kappa, overall)
 *   4 - NPofB20    (proportion of bugs found in top-20% by predicted probability)
 *
 * NaN metric values (produced when a class has zero predictions in all folds)
 * are treated as 0.0 so they do not propagate to the CSV output.
 */
public class ClassifierEvaluatorService {

    private static final Logger log = LoggerFactory.getLogger(ClassifierEvaluatorService.class);

    private final NpofB20Service npofB20Service;

    public ClassifierEvaluatorService(NpofB20Service npofB20Service) {
        this.npofB20Service = npofB20Service;
    }

    public double[] evaluate(Instances data,
                             Classifier classifier,
                             int runs,
                             int folds,
                             int posClassIndex) throws Exception {

        double sumPrecision = 0;
        double sumRecall = 0;
        double sumAuc = 0;
        double sumKappa = 0;
        double sumNpofB = 0;

        for (int run = 0; run < runs; run++) {
            Evaluation eval = new Evaluation(data);
            // crossValidateModel creates its own copy of data, randomizes with the
            // provided seed, stratifies and builds fold splits internally.
            // Passing data directly (no pre-shuffle) avoids double-randomization
            // which would produce different fold splits than intended.
            eval.crossValidateModel(classifier, data, folds, new Random(run));

            double precision = safe(eval.precision(posClassIndex));
            double recall    = safe(eval.recall(posClassIndex));
            double auc       = safe(eval.areaUnderROC(posClassIndex));
            double kappa     = safe(eval.kappa());
            double npofB     = npofB20Service.compute(eval, posClassIndex);

            sumPrecision += precision;
            sumRecall    += recall;
            sumAuc       += auc;
            sumKappa     += kappa;
            sumNpofB     += npofB;

            if (log.isDebugEnabled()) {
                log.debug("Run {}/{}: precision={} recall={} auc={} kappa={}",
                        run + 1, runs,
                        String.format("%.3f", precision),
                        String.format("%.3f", recall),
                        String.format("%.3f", auc),
                        String.format("%.3f", kappa));
            }
        }

        return new double[]{
            sumPrecision / runs,
            sumRecall    / runs,
            sumAuc       / runs,
            sumKappa     / runs,
            sumNpofB     / runs
        };
    }

    private double safe(double v) {
        return Double.isNaN(v) || Double.isInfinite(v) ? 0.0 : v;
    }
}