package com.isw2project.classifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SpreadSubsample;

/**
 * Builds the Weka supervised-instance filter for a given BalancingStrategy.
 *
 * Balancing is applied only on the training fold (inside FilteredClassifier), never on
 * the test fold. The two sets have opposite roles: the training set must be modified so
 * the classifier cannot take the shortcut of always predicting the majority class; the
 * test set must be left untouched because it represents reality — the real class
 * distribution the model will face in production.
 *
 * Class-ratio parameters (SMOTE percentage, Resample size) are estimated from
 * the full dataset passed as argument. The per-fold deviation from these values
 * is negligible (approximately 10%) and acceptable for coarse-grained balancing.
 *
 * Returns null for BalancingStrategy.NONE.
 */
public class BalancingBuilderService {

    private static final Logger log = LoggerFactory.getLogger(BalancingBuilderService.class);

    public Filter build(BalancingStrategy strategy, Instances data) {
        return switch (strategy) {
            case NONE -> null;
            case UNDERSAMPLING -> buildUndersampling();
            case OVERSAMPLING -> buildOversampling(data);
            case SMOTE -> buildSmote(data);
        };
    }

    /**
     * SpreadSubsample removes majority instances until all classes have equal counts.
     * The minority class is left untouched. distributionSpread=1.0 means uniform distribution.
     */
    private SpreadSubsample buildUndersampling() {
        SpreadSubsample filter = new SpreadSubsample();
        filter.setDistributionSpread(1.0);
        return filter;
    }

    /**
     * Resample does not add instances directly to the minority class. Instead it
     * resamples the entire dataset with replacement, drawing each instance with a
     * probability biased toward a uniform class distribution (biasToUniformClass=1.0).
     * The result is a new dataset of sampleSizePercent size where minority instances
     * appear more frequently (drawn more often) and majority instances appear less
     * frequently (drawn less often) than in the original.
     *
     * sampleSizePercent is set to 2 * majority / total so that the output contains
     * approximately majority instances per class, effectively matching minority to majority.
     *
     * Resample is used because it is the only oversampling filter available in the
     * weka-stable core jar. A simpler "duplicate minority instances directly" approach
     * would require a custom filter (as done for SMOTE); Resample achieves the same
     * practical result without additional code.
     */
    private Resample buildOversampling(Instances data) {
        int[] counts = data.attributeStats(data.classIndex()).nominalCounts;
        int majority = 0;
        for (int count : counts) {
            majority = Math.max(majority, count);
        }
        double sampleSizePercent = 2.0 * majority / data.numInstances() * 100.0;

        Resample filter = new Resample();
        filter.setBiasToUniformClass(1.0);
        filter.setNoReplacement(false);
        filter.setSampleSizePercent(sampleSizePercent);
        if (log.isDebugEnabled()) {
            log.debug("Oversampling sampleSizePercent = {}", String.format("%.1f", sampleSizePercent));
        }
        return filter;
    }

    /**
     * SmoteFilter generates synthetic minority-class instances by interpolating between
     * real minority instances in feature space. The percentage is set so that the
     * number of synthetic instances approximately closes the gap between majority
     * and minority class counts.
     *
     * Uses the custom SmoteFilter (weka-stable does not ship SMOTE in its core jar).
     */
    private SmoteFilter buildSmote(Instances data) {
        int[] counts = data.attributeStats(data.classIndex()).nominalCounts;
        int majority = 0;
        int minority = Integer.MAX_VALUE;
        int minorityIndex = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > majority) majority = counts[i];
            if (counts[i] < minority) {
                minority = counts[i];
                minorityIndex = i;
            }
        }

        double smotePercentage = (double) (majority - minority) / minority * 100.0;
        smotePercentage = Math.max(100.0, smotePercentage);

        SmoteFilter filter = new SmoteFilter();
        filter.setMinorityClassIndex(minorityIndex);
        filter.setPercentage(smotePercentage);
        if (log.isDebugEnabled()) {
            log.debug("SMOTE percentage = {} (minority class index = {})",
                    String.format("%.1f", smotePercentage), minorityIndex);
        }
        return filter;
    }
}
