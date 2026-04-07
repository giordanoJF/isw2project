package com.isw2project.downloader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isw2project.config.ProjectConfig;
import com.isw2project.model.Issue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class IssueDownloader {
    private final JiraClient client;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(IssueDownloader.class);


    public IssueDownloader(JiraClient client) {
        this.client = client;
        this.objectMapper = new ObjectMapper();
    }

    List<Issue> downloadIssues(ProjectConfig projectConfig) {
        String jql = JqlBuilder.build(projectConfig.getKey(), projectConfig.getJql());
        log.info("  JQL: {}", jql);

        List<Issue> issues = new ArrayList<>();
        int startAt = 0;
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
}