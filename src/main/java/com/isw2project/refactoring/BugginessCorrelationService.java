package com.isw2project.refactoring;

import com.isw2project.classifier.WekaDatasetService;
import com.isw2project.whatif.CorrelationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.Instances;

import java.util.List;
import java.util.Map;

public class BugginessCorrelationService {

    private static final Logger log = LoggerFactory.getLogger(BugginessCorrelationService.class);

    private final WekaDatasetService datasetService;
    private final CorrelationService correlationService;

    public BugginessCorrelationService(WekaDatasetService datasetService,
                                        CorrelationService correlationService) {
        this.datasetService = datasetService;
        this.correlationService = correlationService;
    }

    public List<Map<String, String>> compute(String inputCsvPath) throws Exception {
        Instances data = datasetService.load(inputCsvPath);
        log.info("Computing Spearman correlation with isBuggy on {} instances", data.numInstances());
        return correlationService.computeCorrelation(data, "isBuggy", "Spearman_with_isBuggy");
    }
}
