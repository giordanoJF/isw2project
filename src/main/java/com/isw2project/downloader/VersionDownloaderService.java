package com.isw2project.downloader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isw2project.model.Version;

import java.util.ArrayList;
import java.util.List;

public class VersionDownloaderService {
    private final JiraRequestService client;
    private final ObjectMapper objectMapper;

    public VersionDownloaderService(JiraRequestService client) {
        this.client = client;
        this.objectMapper = new ObjectMapper();
    }

    List<Version> downloadVersions(String projectKey) {
        JsonNode versionsNode = client.getVersions(projectKey);
        List<Version> versions = new ArrayList<>();
        for (JsonNode node : versionsNode) {
            versions.add(objectMapper.convertValue(node, Version.class));
        }
        return versions;
    }
}