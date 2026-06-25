package com.isw2project;

import com.isw2project.config.AppConfig;
import com.isw2project.config.ConfigLoader;
import com.isw2project.config.RefactoringConfig;
import com.isw2project.gitextractor.GitFileExtractorService;
import com.isw2project.gitextractor.JavaClassFilterService;
import com.isw2project.refactoring.ClassSelectionOrchestrator;
import com.isw2project.refactoring.ClassSelectorService;
import com.isw2project.refactoring.ClassSizeFilterService;
import com.isw2project.refactoring.NsmellsService;
import com.isw2project.refactoring.ReleaseSourceService;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;

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

        ClassSelectionOrchestrator orchestrator = new ClassSelectionOrchestrator(
                new ReleaseSourceService(gitExtractor),
                new NsmellsService(classFilter),
                new ClassSizeFilterService(),
                new ClassSelectorService()
        );
        orchestrator.run(refactoring, batchSize, cpuFraction);
    }
}
