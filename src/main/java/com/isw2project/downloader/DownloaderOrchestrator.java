package com.isw2project.downloader;

import com.isw2project.config.AppConfig;
import com.isw2project.config.ProjectConfig;
import com.isw2project.model.Issue;
import com.isw2project.model.ProjectData;
import com.isw2project.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the full download process for all configured projects.
 */
public class DownloaderOrchestrator {

    private final AppConfig config;
    private final IssueDownloaderService issueDownloaderService;
    private final VersionDownloaderService versionDownloaderService;

    private static final Logger log = LoggerFactory.getLogger(DownloaderOrchestrator.class);

    public DownloaderOrchestrator(AppConfig config) {
        this.config = config;

        JiraRequestService client = new JiraRequestService(config.getBaseUrl());

        this.issueDownloaderService = new IssueDownloaderService(client);
        this.versionDownloaderService = new VersionDownloaderService(client);
    }

    /**
     * Downloads data for all projects defined in the config.
     *
     * @return list of ProjectData, one per configured project
     */
    public List<ProjectData> downloadAll() {
        List<ProjectData> results = new ArrayList<>();
        for (ProjectConfig projectConfig : config.getProjects()) {
            log.info("Downloading project: {}", projectConfig.getKey());
            ProjectData data = downloadProject(projectConfig);
            results.add(data);
        }

        results.forEach(projectData ->
                log.info("Project [{}]: {} issues, {} versions downloaded.\n",
                        projectData.getProjectKey(),
                        projectData.getIssues().size(),
                        projectData.getVersions().size()));

        return results;
    }

    // -------------------------------------------------------------------------

    private ProjectData downloadProject(ProjectConfig projectConfig) {
        List<Issue> issues     = issueDownloaderService.downloadIssues(projectConfig);
        List<Version> versions = versionDownloaderService.downloadVersions(projectConfig.getKey());
        return new ProjectData(projectConfig.getKey(), issues, versions);
    }
}