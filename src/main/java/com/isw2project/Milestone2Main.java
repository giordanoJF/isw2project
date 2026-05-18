package com.isw2project;

import com.isw2project.classifier.BalancingBuilderService;
import com.isw2project.classifier.ClassifierBuilderService;
import com.isw2project.classifier.ClassifierEvaluatorService;
import com.isw2project.classifier.ClassifierOrchestrator;
import com.isw2project.classifier.ClassifierResultRowMapperService;
import com.isw2project.classifier.FeatureSelectionBuilderService;
import com.isw2project.config.InteractiveParallelismConfigurator;
import com.isw2project.classifier.NpofB20Service;
import com.isw2project.classifier.WekaDatasetService;
import com.isw2project.config.AppConfig;
import com.isw2project.config.ClassifierConfig;
import com.isw2project.config.ConfigLoader;
import com.isw2project.config.ParallelismConfig;
import com.isw2project.csv.CsvWriterService;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * Standalone entry point for Milestone 2.
 *
 * Reads the snapshot CSV produced by Milestone 1 from disk (path configured in
 * config.yaml under classifier.inputCsv) and runs the full classifier evaluation
 * pipeline without executing any Milestone 1 step.
 *
 * If config.yaml has parallelism.interactive = true, the user is prompted at
 * startup to choose thread counts; otherwise the values in config.yaml are used
 * (integers or percentages, e.g. "8" or "50%").
 *
 */
public class Milestone2Main {

    @SuppressWarnings("java:S1172")
    public static void main(String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        AppConfig config = ConfigLoader.load("config.yaml");
        ClassifierConfig clf = config.getClassifier();

        if (clf.getParallelism().isInteractive()) {
            int totalCombinations = clf.getClassifiers().size()
                    * clf.getFeatureSelection().size()
                    * clf.getBalancing().size();
            ParallelismConfig resolved = InteractiveParallelismConfigurator.configure(totalCombinations);
            clf.setParallelism(resolved);
        }

        NpofB20Service npofB20Service = new NpofB20Service();
        FeatureSelectionBuilderService fsBuilder  = new FeatureSelectionBuilderService();
        BalancingBuilderService        balBuilder = new BalancingBuilderService();

        ClassifierOrchestrator orchestrator = new ClassifierOrchestrator(
                clf,
                new WekaDatasetService(),
                new ClassifierBuilderService(fsBuilder, balBuilder),
                new ClassifierEvaluatorService(npofB20Service),
                new ClassifierResultRowMapperService(),
                new CsvWriterService()
        );
        orchestrator.run();
    }
}
