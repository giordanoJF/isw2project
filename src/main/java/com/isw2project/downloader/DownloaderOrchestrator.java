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
    private final IssueDownloader issueDownloader;
    private final VersionDownloader versionDownloader;

    private static final Logger log = LoggerFactory.getLogger(DownloaderOrchestrator.class);

    public DownloaderOrchestrator(AppConfig config) {
        this.config = config;

        JiraClient client = new JiraClient(config.getBaseUrl());

        this.issueDownloader = new IssueDownloader(client);
        this.versionDownloader = new VersionDownloader(client);
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
        return results;
    }

    // -------------------------------------------------------------------------

    private ProjectData downloadProject(ProjectConfig projectConfig) {
        List<Issue> issues     = issueDownloader.downloadIssues(projectConfig);
        List<Version> versions = versionDownloader.downloadVersions(projectConfig.getKey());
        return new ProjectData(projectConfig.getKey(), issues, versions);
    }
}