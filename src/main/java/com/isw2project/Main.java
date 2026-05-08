    package com.isw2project;

    import com.isw2project.buggyness.BugginessLabelerService;
    import com.isw2project.buggyness.BugginessOrchestrator;
    import com.isw2project.gitextractor.support.CommitIndexService;
    import com.isw2project.config.AppConfig;
    import com.isw2project.config.ConfigLoader;
    import com.isw2project.consistency.ConsistencyOrchestrator;
    import com.isw2project.csv.*;
    import com.isw2project.downloader.DownloaderOrchestrator;
    import com.isw2project.enricher.EnricherOrchestrator;
    import com.isw2project.enricher.VersionService;
    import com.isw2project.gitextractor.GitExtractorOrchestrator;
    import com.isw2project.metrics.*;
    import com.isw2project.model.ProjectData;
    import com.isw2project.model.ReleaseSnapshot;
    import com.isw2project.proportion.InjectedVersionService;
    import com.isw2project.proportion.ProportionOrchestrator;
    import com.isw2project.proportion.ProportionService;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;

    import java.io.File;
    import java.util.List;
    import java.util.Map;
    import java.util.Set;

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

            // Download versions and issues
            List<ProjectData> result = downloader.downloadAll();
            csvExporter.export(result, "originalResult");

            // Instantiate the enricher orchestrator
            EnricherOrchestrator enricher = new EnricherOrchestrator(
                    new VersionService());

            // Enrich issuess
            List<ProjectData> enriched;
            enriched = enricher.cleanIssueVersionsWithoutReleaseDate(result);
            enriched = enricher.enrichAVFromFV(enriched);
            enriched = enricher.enrichWithOV(enriched);
            enriched = enricher.enrichMultiToSingleFV(enriched);
            csvExporter.export(enriched, "enrichedResult");

            // Instantiate the consistency orchestrator
            ConsistencyOrchestrator consistency = new ConsistencyOrchestrator();

            // Consistency checks
            List<ProjectData> cleaned = consistency.checkAll(enriched);

            // Remove zombie versions
            cleaned = enricher.removeZombieVersionReferences(cleaned);
            csvExporter.export(cleaned, "checkedResult");

            // Instantiate the proportion orchestrator
            ProportionOrchestrator proportionOrchestrator = new ProportionOrchestrator(
                    new ProportionService(),
                    new InjectedVersionService()
            );

            // Proportion
            proportionOrchestrator.applyProportion(cleaned);

            // Remove last issues with no AV
            cleaned = consistency.checkIssueHasAffectedVersion(cleaned);
            csvExporter.export(cleaned, "proportionResult");

            log.info("=== Post-proportion validation ===");
            consistency.checkAll(cleaned);

            File repoDir = new File(config.getGit().getRepoDir());
            File codeOutputDir = new File(config.getGit().getCodeOutputDir());

            CommitIndexService commitIndexService = new CommitIndexService(repoDir);
            Map<String, Set<String>> issueToFilesIndex = commitIndexService.buildIssueToFilesIndex();

            GitExtractorOrchestrator gitOrchestrator = new GitExtractorOrchestrator(repoDir, codeOutputDir, false);
            List<ReleaseSnapshot> snapshots = gitOrchestrator.extractSnapshots(cleaned.getFirst().getVersions());

            BugginessOrchestrator bugginessOrchestrator = new BugginessOrchestrator(commitIndexService, new BugginessLabelerService());
            bugginessOrchestrator.labelSnapshots(snapshots, cleaned.getFirst().getIssues());

            MetricsOrchestrator metricsOrchestrator = new MetricsOrchestrator(
                    repoDir,
                    cleaned.getFirst().getVersions(),
                    gitOrchestrator.getLastResolvedRefs(),
                    cleaned.getFirst().getIssues(),
                    issueToFilesIndex
            );
            metricsOrchestrator.computeAll(snapshots,1);

            csvExporter.exportSnapshots(snapshots, "OPENJPA", "snapshotResult");

        }
    }