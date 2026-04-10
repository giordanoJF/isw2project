package com.isw2project.enricher;

import com.isw2project.model.ProjectData;

import java.util.List;

public class EnricherOrchestrator {

    private final VersionOpsService versionOpsService;

    public EnricherOrchestrator(VersionOpsService versionOpsService) {
        this.versionOpsService = versionOpsService;
    }

    public List<ProjectData> enrichWithOV(List<ProjectData> projects, boolean updateOriginal) {
        List<ProjectData> enriched = projects.stream()
                .map(p -> {
                    p.getIssues().forEach(issue ->
                            versionOpsService.assignOpeningVersion(issue, p.getVersions(), p.getProjectKey()));
                    return p;
                })
                .toList();

        if (updateOriginal) {
            projects.clear();
            projects.addAll(enriched);
        }

        return enriched;
    }

    public List<ProjectData> enrichWithFixVersion(List<ProjectData> projects, boolean updateOriginal) {
        List<ProjectData> enriched = projects.stream()
                .map(p -> {
                    p.getIssues().forEach(versionOpsService::assignMostRecentFixVersion);
                    return p;
                })
                .toList();

        if (updateOriginal) {
            projects.clear();
            projects.addAll(enriched);
        }

        return enriched;
    }
}