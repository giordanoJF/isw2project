package com.isw2project;

import com.isw2project.classifier.BalancingBuilderService;
import com.isw2project.classifier.ClassifierBuilderService;
import com.isw2project.classifier.ClassifierEvaluatorService;
import com.isw2project.classifier.FeatureSelectionBuilderService;
import com.isw2project.classifier.NpofB20Service;
import com.isw2project.config.AppConfig;
import com.isw2project.config.ConfigLoader;
import com.isw2project.config.WhatIfConfig;
import com.isw2project.csv.CsvWriterService;
import com.isw2project.whatif.AblationService;
import com.isw2project.whatif.CorrelationService;
import com.isw2project.whatif.WhatIfDatasetService;
import com.isw2project.whatif.WhatIfOrchestrator;
import com.isw2project.whatif.WhatIfPredictorService;
import com.isw2project.whatif.WhatIfTableService;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class Milestone3Main {

    @SuppressWarnings("java:S1172")
    public static void main(String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        AppConfig config    = ConfigLoader.load("config.yaml");
        WhatIfConfig whatif = config.getWhatif();

        ClassifierBuilderService classifierBuilder = new ClassifierBuilderService(
                new FeatureSelectionBuilderService(),
                new BalancingBuilderService()
        );
        ClassifierEvaluatorService evaluator = new ClassifierEvaluatorService(new NpofB20Service());

        WhatIfOrchestrator orchestrator = new WhatIfOrchestrator(
                whatif,
                new WhatIfDatasetService(),
                new WhatIfPredictorService(),
                new WhatIfTableService(),
                new CorrelationService(),
                new AblationService(classifierBuilder, evaluator),
                new CsvWriterService()
        );
        orchestrator.run();
    }
}
