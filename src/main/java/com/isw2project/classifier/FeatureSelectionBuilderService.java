package com.isw2project.classifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.attributeSelection.WrapperSubsetEval;
import weka.attributeSelection.GreedyStepwise;
import weka.classifiers.bayes.NaiveBayes;
import weka.filters.supervised.attribute.AttributeSelection;

/**
 * Builds the Weka AttributeSelection filter corresponding to a given FeatureSelectionStrategy.
 *
 * Filter methods (InfoGain, Spearman) use a Ranker with threshold 0.0, which discards
 * attributes with zero information relative to the class.
 *
 * Wrapper methods (ForwardSearch, BackwardSearch) use WrapperSubsetEval with NaiveBayes
 * as the internal classifier and GreedyStepwise as the search algorithm. These are
 * correct but computationally expensive: the wrapper search is executed once per outer
 * cross-validation fold (100 times in 10x10-fold CV).
 *
 * Returns null for FeatureSelectionStrategy.NONE so callers can skip filter construction.
 */
public class FeatureSelectionBuilderService {

    private static final Logger log = LoggerFactory.getLogger(FeatureSelectionBuilderService.class);

    public AttributeSelection build(FeatureSelectionStrategy strategy) {
        return switch (strategy) {
            case NONE -> null;
            case INFO_GAIN -> buildInfoGain();
            case SPEARMAN -> buildSpearman();
            case FORWARD_SEARCH -> {
                log.warn("ForwardSearch is a wrapper method: expect very long runtime with 10x10-fold CV");
                yield buildWrapper(false);
            }
            case BACKWARD_SEARCH -> {
                log.warn("BackwardSearch is a wrapper method: expect very long runtime with 10x10-fold CV");
                yield buildWrapper(true);
            }
        };
    }

    private AttributeSelection buildInfoGain() {
        Ranker ranker = new Ranker();
        ranker.setThreshold(0.0);

        AttributeSelection filter = new AttributeSelection();
        filter.setEvaluator(new InfoGainAttributeEval());
        filter.setSearch(ranker);
        return filter;
    }

    private AttributeSelection buildSpearman() {
        Ranker ranker = new Ranker();
        ranker.setThreshold(0.0);

        AttributeSelection filter = new AttributeSelection();
        filter.setEvaluator(new SpearmanAttributeEval());
        filter.setSearch(ranker);
        return filter;
    }

    private AttributeSelection buildWrapper(boolean searchBackwards) {
        // WrapperSubsetEval evaluates each attribute subset by training a
        // classifier (NaiveBayes) and measuring its performance via inner CV.
        WrapperSubsetEval wrapperEval = new WrapperSubsetEval();
        wrapperEval.setClassifier(new NaiveBayes());

        // GreedyStepwise cerca il miglior sottoinsieme di attributi in modo incrementale.
        // Se searchBackwards=true: parte da tutti gli attributi e rimuove quelli che peggiorano.
        // Se searchBackwards=false: parte da nessun attributo e aggiunge quelli che migliorano.
        // Sceglie sempre l'opzione localmente ottimale a ogni passo (greedy approach).
        GreedyStepwise search = new GreedyStepwise();
        search.setSearchBackwards(searchBackwards);

        AttributeSelection filter = new AttributeSelection();
        filter.setEvaluator(wrapperEval);
        filter.setSearch(search);
        return filter;
    }
}
