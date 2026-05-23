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
    import com.isw2project.repocloner.RepoCloneOrchestrator;
    import com.isw2project.repocloner.RepoCloneService;
    import org.eclipse.jgit.api.errors.GitAPIException;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;

    import java.io.File;
    import java.util.List;
    import java.util.Map;
    import java.util.Set;

    /**
     * Entry point for Milestone 1.
     * Downloads Jira data, applies enrichment and proportion, extracts Git snapshots,
     * labels bugginess, computes metrics, and writes output/snapshotResult/OPENJPA_snapshots.csv.
     */
    public class Milestone1Main {

        private static final Logger log = LoggerFactory.getLogger(Milestone1Main.class);

        @SuppressWarnings("java:S1172")
    public static void main(String[] args) {

            // Load the configuration
            AppConfig config = ConfigLoader.load("config.yaml");

            // Ensure the project repository is present locally before any Git operations
            try {
                new RepoCloneOrchestrator(new RepoCloneService()).ensureCloned(config.getGit());
            } catch (GitAPIException e) {
                throw new IllegalStateException("Failed to clone repository: " + e.getMessage(), e);
            }

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
            csvExporter.export(result, "1_raw_jira_data");

            // Instantiate the enricher orchestrator
            EnricherOrchestrator enricher = new EnricherOrchestrator(
                    new VersionService());

            // Enrich issuess
            List<ProjectData> enriched;
            enriched = enricher.cleanIssueVersionsWithoutReleaseDate(result);
            enriched = enricher.enrichAVFromFV(enriched);
            enriched = enricher.enrichWithOV(enriched);
            enriched = enricher.enrichMultiToSingleFV(enriched);
            csvExporter.export(enriched, "2_enriched_jira_data");

            // Instantiate the consistency orchestrator
            ConsistencyOrchestrator consistency = new ConsistencyOrchestrator();

            // Consistency checks
            List<ProjectData> cleaned = consistency.checkAll(enriched);

            // Remove zombie versions
            cleaned = enricher.removeZombieVersionReferences(cleaned);
            csvExporter.export(cleaned, "3_consistency_checked");

            // Instantiate the proportion orchestrator
            ProportionOrchestrator proportionOrchestrator = new ProportionOrchestrator(
                    new ProportionService(),
                    new InjectedVersionService()
            );

            // Proportion
            proportionOrchestrator.applyProportion(cleaned);

            // Remove last issues with no AV
            cleaned = consistency.checkIssueHasAffectedVersion(cleaned);
            csvExporter.export(cleaned, "4_proportion_applied");

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
                    issueToFilesIndex,
                    config.getMetrics()
            );
            metricsOrchestrator.computeAll(snapshots);

            csvExporter.exportSnapshots(snapshots, "OPENJPA", "5_snapshots");

        }
    }