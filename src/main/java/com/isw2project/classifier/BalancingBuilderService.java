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
 * test set must be left untouched because it represents reality - the real class
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
     * SmoteFilter is the official Weka SMOTE source (Lichtenwalter 2008) re-packaged
     * in this project because the Weka SMOTE package is not on Maven Central.
     *
     * For each minority-class instance it finds the k nearest neighbors using Euclidean
     * distance for numeric attributes and VDM (Value Distance Metric) for nominal ones,
     * then generates floor(percentage/100) synthetic instances per base instance by
     * interpolating toward a randomly chosen neighbor. Nominal attributes are resolved
     * by majority vote among the k neighbors. The minority class is auto-detected.
     *
     * The percentage is computed from the full dataset so that synthetic instances
     * approximately close the gap between majority and minority class counts.
     */
    private SmoteFilter buildSmote(Instances data) {
        int[] counts = data.attributeStats(data.classIndex()).nominalCounts;
        int majority = 0;
        int minority = Integer.MAX_VALUE;
        for (int count : counts) {
            if (count > majority) majority = count;
            if (count < minority) minority = count;
        }

        double smotePercentage = (double) (majority - minority) / minority * 100.0;
        smotePercentage = Math.max(100.0, smotePercentage);

        SmoteFilter filter = new SmoteFilter();
        filter.setPercentage(smotePercentage);
        if (log.isDebugEnabled()) {
            log.debug("SMOTE percentage = {}", String.format("%.1f", smotePercentage));
        }
        return filter;
    }
}
