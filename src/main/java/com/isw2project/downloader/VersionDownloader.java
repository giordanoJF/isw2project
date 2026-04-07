package com.isw2project.downloader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isw2project.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class VersionDownloader {
    private final JiraClient client;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(VersionDownloader.class);

    public VersionDownloader(JiraClient client) {
        this.client = client;
        this.objectMapper = new ObjectMapper();
    }

    List<Version> downloadVersions(String projectKey) {
        JsonNode versionsNode = client.getVersions(projectKey);
        List<Version> versions = new ArrayList<>();
        for (JsonNode node : versionsNode) {
            versions.add(objectMapper.convertValue(node, Version.class));
        }
        log.info("  Versions fetched: {}", versions.size());
        return versions;
    }
}