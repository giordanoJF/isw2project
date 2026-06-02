package com.isw2project.whatif;

import com.isw2project.classifier.BalancingBuilderService;
import com.isw2project.classifier.BalancingStrategy;
import com.isw2project.classifier.ClassifierBuilderService;
import com.isw2project.classifier.ClassifierType;
import com.isw2project.classifier.FeatureSelectionBuilderService;
import com.isw2project.classifier.FeatureSelectionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.core.Instances;

public class WhatIfPredictorService {

    private static final Logger log = LoggerFactory.getLogger(WhatIfPredictorService.class);

    private final ClassifierBuilderService classifierBuilder;

    public WhatIfPredictorService() {
        this.classifierBuilder = new ClassifierBuilderService(
                new FeatureSelectionBuilderService(),
                new BalancingBuilderService()
        );
    }

    public record Predictions(boolean[] a, boolean[] bPlus, boolean[] b, boolean[] c) {}

    public Predictions trainAndPredict(WhatIfDatasetService.Datasets datasets,
                                       ClassifierType clfType,
                                       FeatureSelectionStrategy fs,
                                       BalancingStrategy bal) throws Exception {
        Instances a     = datasets.a();
        Instances bPlus = datasets.bPlus();
        Instances bData = datasets.b();
        Instances c     = datasets.c();

        log.info("Building BClassifier: {} + {} + {}", clfType, fs, bal);
        Classifier clf = classifierBuilder.build(clfType, fs, bal, a, 1);

        log.info("Training BClassifierA on A ({} instances)...", a.numInstances());
        clf.buildClassifier(a);
        log.info("Training complete");

        int posIdx = a.classAttribute().indexOfValue("true");

        log.info("Predicting on A, B+, B, C...");
        return new Predictions(
                predict(clf, a, posIdx),
                predict(clf, bPlus, posIdx),
                predict(clf, bData, posIdx),
                predict(clf, c, posIdx)
        );
    }

    private boolean[] predict(Classifier clf, Instances data, int posIdx) throws Exception {
        boolean[] result = new boolean[data.numInstances()];
        for (int i = 0; i < data.numInstances(); i++) {
            result[i] = (int) clf.classifyInstance(data.instance(i)) == posIdx;
        }
        return result;
    }
}
