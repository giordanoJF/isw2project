package com.isw2project.enricher;

import com.isw2project.model.Issue;
import com.isw2project.model.ProjectData;
import com.isw2project.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class EnricherOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(EnricherOrchestrator.class);

    private final VersionDateService versionDateService;

    public EnricherOrchestrator(VersionDateService versionDateService) {
        this.versionDateService = versionDateService;
    }

    public List<ProjectData> enrichWithOV(List<ProjectData> projects, boolean updateOriginal) {
        List<ProjectData> enriched = projects.stream()
                .map(this::enrichProjectWithOV)
                .toList();

        if (updateOriginal) {
            projects.clear();
            projects.addAll(enriched);
        }

        return enriched;
    }

    private ProjectData enrichProjectWithOV(ProjectData projectData) {
        List<Issue> enrichedIssues = projectData.getIssues().stream()
                .map(issue -> enrichIssueWithOV(issue, projectData.getVersions(), projectData.getProjectKey()))
                .toList();

        return new ProjectData(projectData.getProjectKey(), enrichedIssues, projectData.getVersions());
    }

    private Issue enrichIssueWithOV(Issue issue, List<Version> versions, String projectKey) {
        String created = issue.getFields().getCreated();
        if (created == null || created.isBlank()) {
            log.warn("Project [{}] issue [{}] has no creation date, skipping opening version.",
                    projectKey, issue.getKey());
            return issue;
        }

        Optional<Version> openingVersion = versionDateService.findClosestVersionBefore(created, versions);

        if (openingVersion.isPresent()) {
            issue.getFields().setOpeningVersion(openingVersion.get());
        } else {
            log.warn("Project [{}] issue [{}] has no opening version.", projectKey, issue.getKey());
        }

        return issue;
    }
}