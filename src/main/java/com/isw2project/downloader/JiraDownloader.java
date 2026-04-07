package com.isw2project.downloader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Delegates HTTP work to JiraClient and JSON mapping to ObjectMapper.
 */
public class JiraDownloader {

    private final AppConfig config;
    private final JiraClient client;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(JiraDownloader.class);

    public JiraDownloader(AppConfig config) {
        this.config       = config;
        this.client       = new JiraClient(config.getBaseUrl());
        this.objectMapper = new ObjectMapper();
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
        List<Issue> issues     = downloadIssues(projectConfig);
        List<Version> versions = downloadVersions(projectConfig.getKey());
        return new ProjectData(projectConfig.getKey(), issues, versions);
    }

    private List<Issue> downloadIssues(ProjectConfig projectConfig) {
        String jql = JqlBuilder.build(projectConfig.getKey(), projectConfig.getJql());
        log.info("  JQL: {}", jql);

        List<Issue> issues = new ArrayList<>();
        int startAt    = 0;
        int maxResults = projectConfig.getMaxResults();
        int total;

        do {
            JsonNode response = client.searchIssues(jql, startAt, maxResults);
            total = response.path("total").asInt();

            for (JsonNode issueNode : response.path("issues")) {
                Issue issue = objectMapper.convertValue(issueNode, Issue.class);
                issues.add(issue);
            }

            startAt += maxResults;
            log.info("  Issues fetched: {} / {}", issues.size(), total);

        } while (startAt < total);

        return issues;
    }

    private List<Version> downloadVersions(String projectKey) {
        JsonNode versionsNode = client.getVersions(projectKey);
        List<Version> versions = new ArrayList<>();
        for (JsonNode node : versionsNode) {
            versions.add(objectMapper.convertValue(node, Version.class));
        }
        log.info("  Versions fetched: {}", versions.size());
        return versions;
    }
}