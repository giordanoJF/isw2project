package com.isw2project.classifier;

import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.filters.Filter;
import weka.filters.MultiFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the final Weka Classifier for a given combination of
 * ClassifierType, FeatureSelectionStrategy, and BalancingStrategy.
 *
 * When filters are present, they are combined in a MultiFilter and wrapped in
 * a FilteredClassifier. This guarantees that balancing and feature selection
 * are applied exclusively on the training fold of every CV split, preventing
 * any form of data leakage into the test fold.
 *
 * Filter application order: balancing first, then feature selection.
 * Rationale: feature selection should observe the balanced class distribution
 * when computing attribute scores (InfoGain, Spearman); applying FS on an
 * imbalanced dataset would bias the scores toward the majority class.
 */
public class ClassifierBuilderService {

    private final FeatureSelectionBuilderService fsBuilder;
    private final BalancingBuilderService balBuilder;

    public ClassifierBuilderService(FeatureSelectionBuilderService fsBuilder,
                                    BalancingBuilderService balBuilder) {
        this.fsBuilder  = fsBuilder;
        this.balBuilder = balBuilder;
    }

    public Classifier build(ClassifierType type,
                            FeatureSelectionStrategy fs,
                            BalancingStrategy bal,
                            weka.core.Instances data,
                            int randomForestSlots) {

        Classifier base = buildBase(type, randomForestSlots);

        Filter fsFilter  = fsBuilder.build(fs);
        Filter balFilter = balBuilder.build(bal, data);

        List<Filter> filters = new ArrayList<>();
        if (balFilter != null) filters.add(balFilter);
        if (fsFilter  != null) filters.add(fsFilter);

        if (filters.isEmpty()) {
            return base;
        }

        MultiFilter multiFilter = new MultiFilter();
        multiFilter.setFilters(filters.toArray(new Filter[0]));

        FilteredClassifier fc = new FilteredClassifier();
        fc.setFilter(multiFilter);
        fc.setClassifier(base);
        return fc;
    }

    private Classifier buildBase(ClassifierType type, int randomForestSlots) {
        return switch (type) {
            case RANDOM_FOREST -> {
                RandomForest rf = new RandomForest();
                rf.setNumExecutionSlots(randomForestSlots);
                yield rf;
            }
            case NAIVE_BAYES -> new NaiveBayes();
            case IBK         -> new IBk();
        };
    }
}
