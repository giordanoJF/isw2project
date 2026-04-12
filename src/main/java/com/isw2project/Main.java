package com.isw2project;

import com.isw2project.config.AppConfig;
import com.isw2project.config.ConfigLoader;
import com.isw2project.consistency.ConsistencyOrchestrator;
import com.isw2project.csv.CsvExporterOrchestrator;
import com.isw2project.csv.CsvWriterService;
import com.isw2project.csv.IssueCsvRowMapperService;
import com.isw2project.csv.VersionCsvRowMapperService;
import com.isw2project.downloader.DownloaderOrchestrator;
import com.isw2project.enricher.EnricherOrchestrator;
import com.isw2project.enricher.VersionService;
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
                new VersionService());

        // Instantiate the consistency orchestrator
        ConsistencyOrchestrator consistency = new ConsistencyOrchestrator();





        // Download versions and issues
        List<ProjectData> result = downloader.downloadAll();
        csvExporter.export(result, "originalResult");


        // Enrich issues
        List<ProjectData> enriched;
        enriched = enricher.cleanIssueVersionsWithoutReleaseDate(result);
        enriched = enricher.enrichAVFromFV(enriched);
        enriched = enricher.enrichWithOV(enriched);
        enriched = enricher.enrichMultiToSingleFV(enriched);
        csvExporter.export(enriched, "enrichedResult");

        // Consistency checks
        List<ProjectData> cleaned;
        cleaned= consistency.checkVersionIsReleased(result);
        cleaned = consistency.checkVersionHasName(cleaned);
        cleaned = consistency.checkVersionHasReleaseDate(cleaned);
        cleaned = consistency.checkIssueHasKey(cleaned);
        cleaned = consistency.checkIssueHasCreatedDate(cleaned);
        cleaned = consistency.checkIssueHasFixVersion(cleaned);
        cleaned = consistency.checkIssueFixVersionHasReleaseDate(cleaned);
        cleaned = consistency.checkIssueCreatedAfterOldestVersion(cleaned);
        cleaned = consistency.checkIssueFixVersionAfterOpeningVersion(cleaned);
        csvExporter.export(cleaned, "checkedResult");

        log.info("Done.");
    }
}