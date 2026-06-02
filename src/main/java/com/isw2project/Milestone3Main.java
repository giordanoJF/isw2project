package com.isw2project;

import com.isw2project.config.AppConfig;
import com.isw2project.config.ConfigLoader;
import com.isw2project.config.WhatIfConfig;
import com.isw2project.csv.CsvWriterService;
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

        WhatIfOrchestrator orchestrator = new WhatIfOrchestrator(
                whatif,
                new WhatIfDatasetService(),
                new WhatIfPredictorService(),
                new WhatIfTableService(),
                new CsvWriterService()
        );
        orchestrator.run();
    }
}
