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

    public void enrichWithOV(List<ProjectData> projects) {
        projects.forEach(this::enrichProjectWithOV);
    }

    private void enrichProjectWithOV(ProjectData projectData) {
        List<Version> versions = projectData.getVersions();
        projectData.getIssues().forEach(issue -> enrichIssueWithOV(issue, versions, projectData.getProjectKey()));
    }

    private void enrichIssueWithOV(Issue issue, List<Version> versions, String projectKey) {
        String created = issue.getFields().getCreated();
        if (created == null || created.isBlank()) {
            log.warn("Project [{}] issue [{}] has no creation date, skipping opening version.",
                    projectKey, issue.getKey());
            return;
        }

        Optional<Version> openingVersion = versionDateService.findClosestVersionBefore(created, versions);

        if (openingVersion.isPresent()) {
            issue.getFields().setOpeningVersion(openingVersion.get());
        } else {
            log.warn("Project [{}] issue [{}] has no opening version.",
                    projectKey, issue.getKey());
        }
    }
}