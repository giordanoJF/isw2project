package com.isw2project;

import com.isw2project.config.AppConfig;
import com.isw2project.config.ConfigLoader;
import com.isw2project.consistency.ConsistencyOrchestrator;
import com.isw2project.csv.*;
import com.isw2project.downloader.DownloaderOrchestrator;
import com.isw2project.enricher.EnricherOrchestrator;
import com.isw2project.enricher.VersionService;
import com.isw2project.gitextractor.GitCommandException;
import com.isw2project.gitextractor.GitExtractorOrchestrator;
import com.isw2project.model.ProjectData;
import com.isw2project.model.ReleaseSnapshot;
import com.isw2project.model.Version;
import com.isw2project.proportion.InjectedVersionService;
import com.isw2project.proportion.ProportionOrchestrator;
import com.isw2project.proportion.ProportionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Entry point for the Jira Downloader application.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);


    static void main() {

        // Load the configuration
        AppConfig config = ConfigLoader.load("config.yaml");

        // Instantiate the downloader orchestrator
        DownloaderOrchestrator downloader = new DownloaderOrchestrator(config);

        // Instantiate the csv orchestrator
        CsvExporterOrchestrator csvExporter = new CsvExporterOrchestrator(
                config.getCsv(),
                new IssueCsvRowMapperService(),
                new VersionCsvRowMapperService(),
                new SnapshotCsvRowMapperService(),
                new CsvWriterService()
        );

        // Instantiate the enricher orchestrator
        EnricherOrchestrator enricher = new EnricherOrchestrator(
                new VersionService());

        // Instantiate the consistency orchestrator
        ConsistencyOrchestrator consistency = new ConsistencyOrchestrator();

        // Instantiate the proportion orchestrator
        ProportionOrchestrator proportionOrchestrator = new ProportionOrchestrator(
                new ProportionService(),
                new InjectedVersionService()
        );







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
        cleaned = consistency.checkIssueFixVersionAfterAffectedVersion(cleaned);
        cleaned = consistency.checkIssueAllVersionsHaveReleaseDate(cleaned);

        // Remove zombie versions
        cleaned = enricher.removeZombieVersionReferences(cleaned);
        csvExporter.export(cleaned, "checkedResult");

        // Proportion
        proportionOrchestrator.applyProportion(cleaned, cleaned);

        // Remove last issues with no AV
        cleaned = consistency.checkIssueHasAffectedVersion(cleaned);
        csvExporter.export(cleaned, "proportionResult");

        log.info("=== Post-proportion validation ===");
        consistency.checkVersionIsReleased(cleaned);
        consistency.checkVersionHasName(cleaned);
        consistency.checkVersionHasReleaseDate(cleaned);
        consistency.checkIssueHasKey(cleaned);
        consistency.checkIssueHasCreatedDate(cleaned);
        consistency.checkIssueHasFixVersion(cleaned);
        consistency.checkIssueFixVersionHasReleaseDate(cleaned);
        consistency.checkIssueCreatedAfterOldestVersion(cleaned);
        consistency.checkIssueFixVersionAfterOpeningVersion(cleaned);
        consistency.checkIssueFixVersionAfterAffectedVersion(cleaned);
        consistency.checkIssueAllVersionsHaveReleaseDate(cleaned);

        //log.info("release names are: {}", cleaned.stream().map(p -> p.getVersions().stream().map(Version::getName).collect(Collectors.joining(", "))).collect(Collectors.joining(", ")));


        File repoDir = new File("gitclones/openjpa");
        File codeOutputDir = new File("output/code");

        try {
            GitExtractorOrchestrator gitOrchestrator = new GitExtractorOrchestrator(repoDir, codeOutputDir, false);
            List<ReleaseSnapshot> snapshots = gitOrchestrator.extractSnapshots(cleaned.getFirst().getVersions());
            csvExporter.exportSnapshots(snapshots, "OPENJPA", "snapshotResult");
        } catch (GitCommandException e) {
            log.error("Git extraction failed", e);
        }




    }
}