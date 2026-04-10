package com.isw2project;

import com.isw2project.config.AppConfig;
import com.isw2project.config.ConfigLoader;
import com.isw2project.consistency.ConsistencyOrchestrator;
import com.isw2project.consistency.checks.*;
import com.isw2project.csv.CsvExporterOrchestrator;
import com.isw2project.csv.CsvWriterService;
import com.isw2project.csv.IssueCsvRowMapperService;
import com.isw2project.csv.VersionCsvRowMapperService;
import com.isw2project.downloader.DownloaderOrchestrator;
import com.isw2project.enricher.EnricherOrchestrator;
import com.isw2project.enricher.VersionOpsService;
import com.isw2project.model.ProjectData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Entry point for the Jira Downloader application.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);


    static void main() {

        log.info("\n\n##### Starting Main #####\n");

        // Load the configuration
        AppConfig config = ConfigLoader.load("config.yaml");

        // Instantiate the downloader orchestrator
        DownloaderOrchestrator downloader = new DownloaderOrchestrator(config);

        // Instantiate the csv orchestrator
        CsvExporterOrchestrator csvExporter = new CsvExporterOrchestrator(
                config.getCsv(),
                new IssueCsvRowMapperService(),
                new VersionCsvRowMapperService(),
                new CsvWriterService()
        );

        // Instantiate the enricher orchestrator
        EnricherOrchestrator enricher = new EnricherOrchestrator(
                new VersionOpsService());





        // Download versions and issues
        List<ProjectData> result = downloader.downloadAll();
        csvExporter.export(result, "originalResult");

        // Delayed instantiation of the consistency orchestrator with custom checks
        ConsistencyOrchestrator checker = new ConsistencyOrchestrator(
                List.of(
                        //these are issues checks that don't require project-specific data
                        new IssueHasKeyCheck(),
                        new IssueHasCreatedDateCheck(),
                        new IssueHasFixVersionCheck(),
                        new IssueFixVersionHasReleaseDateCheck(),
                        new IssueFixVersionAfterOpeningVersionCheck()
                ),
                List.of(
                        //these are version checks that don't require project-specific data'
                        new VersionHasNameCheck(),
                        new VersionIsReleasedCheck(),
                        new VersionHasReleaseDateCheck()
                ),
                List.of(
                        //these are issues check that require project-specific data
                        IssueCreatedAfterOldestVersionCheck::new
                )
        );

        // Enrich issues
        // we need to enrich before the checks, we need the OV
        enricher.enrichWithOV(result, true);
        enricher.enrichWithFixVersion(result, true);
        csvExporter.export(result, "enrichedResult");

        // Consistency check
        checker.clean(result, true);
        csvExporter.export(result, "filteredResult");

        log.info("Done.");
    }
}