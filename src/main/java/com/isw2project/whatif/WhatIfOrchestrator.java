package com.isw2project.whatif;

import com.isw2project.classifier.BalancingStrategy;
import com.isw2project.classifier.ClassifierType;
import com.isw2project.classifier.FeatureSelectionStrategy;
import com.isw2project.config.WhatIfConfig;
import com.isw2project.csv.CsvWriterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class WhatIfOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(WhatIfOrchestrator.class);

    private final WhatIfConfig config;
    private final WhatIfDatasetService datasetService;
    private final WhatIfPredictorService predictorService;
    private final WhatIfTableService tableService;
    private final CsvWriterService csvWriter;

    public WhatIfOrchestrator(WhatIfConfig config,
                               WhatIfDatasetService datasetService,
                               WhatIfPredictorService predictorService,
                               WhatIfTableService tableService,
                               CsvWriterService csvWriter) {
        this.config           = config;
        this.datasetService   = datasetService;
        this.predictorService = predictorService;
        this.tableService     = tableService;
        this.csvWriter        = csvWriter;
    }

    public void run() {
        log.info("=== Milestone 3 - What-If Analysis ===");

        WhatIfDatasetService.Datasets datasets;
        try {
            datasets = datasetService.loadAndSplit(config.getInputCsv(), config.getSmellsColumn());
        } catch (Exception e) {
            log.error("Failed to load dataset: {}", e.getMessage());
            return;
        }

        ClassifierType        clfType = ClassifierType.valueOf(config.getBestClassifier());
        FeatureSelectionStrategy fs   = FeatureSelectionStrategy.valueOf(config.getBestFs());
        BalancingStrategy        bal  = BalancingStrategy.valueOf(config.getBestBalancing());

        WhatIfPredictorService.Predictions preds;
        try {
            preds = predictorService.trainAndPredict(datasets, clfType, fs, bal);
        } catch (Exception e) {
            log.error("Prediction failed: {}", e.getMessage());
            return;
        }

        int actualBuggyA = tableService.countActualBuggy(datasets.a());

        List<Map<String, String>> datasetRows    = tableService.buildDatasetRows(datasets, preds);
        List<Map<String, String>> preventionRows = tableService.buildPreventionRows(preds, actualBuggyA);

        String prefix = config.getDatasetName();
        csvWriter.write(config.getOutputDir(), prefix + "_whatif_datasets.csv",   datasetRows);
        csvWriter.write(config.getOutputDir(), prefix + "_whatif_prevention.csv", preventionRows);

        log.info("Results written to {}/", config.getOutputDir());
        log.info("=== Milestone 3 complete ===");
    }
}
