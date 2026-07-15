package com.isw2project;

import com.isw2project.classifier.BalancingBuilderService;
import com.isw2project.classifier.ClassifierBuilderService;
import com.isw2project.classifier.FeatureSelectionBuilderService;
import com.isw2project.classifier.WekaDatasetService;
import com.isw2project.config.AppConfig;
import com.isw2project.config.ConfigLoader;
import com.isw2project.config.RefactoringConfig;
import com.isw2project.csv.CsvWriterService;
import com.isw2project.gitextractor.GitFileExtractorService;
import com.isw2project.gitextractor.JavaClassFilterService;
import com.isw2project.refactoring.*;
import com.isw2project.whatif.CorrelationService;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.nio.file.Path;

public class Milestone4Main {

    @SuppressWarnings("java:S1172")
    public static void main(String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        AppConfig config = ConfigLoader.load("config.yaml");
        RefactoringConfig refactoring = config.getRefactoring();
        int batchSize      = config.getMetrics().getPmd().getBatchSize();
        double cpuFraction = config.getMetrics().getPmd().getCpuFraction();

        JavaClassFilterService classFilter = new JavaClassFilterService();
        GitFileExtractorService gitExtractor = new GitFileExtractorService(
                new File(config.getGit().getRepoDir()),
                new File(refactoring.getExtractedSourceDir()),
                classFilter
        );

        NsmellsService nsmellsService = new NsmellsService(classFilter);

        ClassSelectionOrchestrator selectionOrchestrator = new ClassSelectionOrchestrator(
                new ReleaseSourceService(gitExtractor),
                nsmellsService,
                new ClassSizeFilterService(),
                new ClassSelectorService(),
                new SmellDetailService()
        );
        selectionOrchestrator.run(refactoring, batchSize, cpuFraction);

        File repoDir = new File(config.getGit().getRepoDir());
        Path refactoredDir = Path.of(refactoring.getRefactoredClassesDir());

        WekaDatasetService datasetService = new WekaDatasetService();
        ClassifierBuilderService classifierBuilder = new ClassifierBuilderService(
                new FeatureSelectionBuilderService(),
                new BalancingBuilderService()
        );

        RefactoringAnalysisOrchestrator analysisOrchestrator = new RefactoringAnalysisOrchestrator(
                new RefactoredMetricsService(repoDir, refactoredDir, nsmellsService, batchSize, cpuFraction),
                new RefactoredPredictorService(datasetService, classifierBuilder),
                new BugginessCorrelationService(datasetService, new CorrelationService()),
                new CsvWriterService()
        );
        analysisOrchestrator.run(refactoring);
    }
}
