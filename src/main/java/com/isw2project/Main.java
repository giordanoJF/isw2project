package com.isw2project;

import com.isw2project.config.AppConfig;
import com.isw2project.config.ConfigLoader;
import com.isw2project.consistency.ConsistencyOrchestrator;
import com.isw2project.consistency.checks.IssueHasCreatedDateCheck;
import com.isw2project.consistency.checks.IssueHasKeyCheck;
import com.isw2project.consistency.checks.VersionHasNameCheck;
import com.isw2project.consistency.checks.VersionIsReleasedCheck;
import com.isw2project.downloader.DownloaderOrchestrator;
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

        AppConfig config = ConfigLoader.load("config.yaml");
        DownloaderOrchestrator downloader = new DownloaderOrchestrator(config);

        List<ProjectData> results = downloader.downloadAll();

        results.forEach(projectData ->
            log.info("Project [{}]: {} issues, {} versions downloaded.",
                    projectData.getProjectKey(),
                    projectData.getIssues().size(),
                    projectData.getVersions().size()));


        ConsistencyOrchestrator checker = new ConsistencyOrchestrator(
                List.of(new IssueHasKeyCheck(), new IssueHasCreatedDateCheck()),
                List.of(new VersionHasNameCheck(), new VersionIsReleasedCheck())
        );

        List<ProjectData> cleaned = checker.clean(results, false);

        cleaned.forEach(projectData ->
            log.info("Cleaned Project [{}]: {} issues, {} versions downloaded.",
                    projectData.getProjectKey(),
                    projectData.getIssues().size(),
                    projectData.getVersions().size()));


        log.info("Done.");
    }
}