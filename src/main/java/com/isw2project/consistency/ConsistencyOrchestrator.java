package com.isw2project.consistency;

import com.isw2project.model.Issue;
import com.isw2project.model.ProjectData;
import com.isw2project.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ConsistencyOrchestrator {

    private final List<IssueCheck> issueChecks;
    private final List<VersionCheck> versionChecks;
    private static final Logger log = LoggerFactory.getLogger(ConsistencyOrchestrator.class);

    public ConsistencyOrchestrator(List<IssueCheck> issueChecks, List<VersionCheck> versionChecks) {
        this.issueChecks   = List.copyOf(issueChecks);
        this.versionChecks = List.copyOf(versionChecks);
    }

    /**
     * Returns a cleaned copy of the input list.
     * If updateOriginal is true, also replaces the contents of the original list.
     */
    public List<ProjectData> clean(List<ProjectData> projects, boolean updateOriginal) {
        List<ProjectData> cleaned = projects.stream()
                .map(this::cleanProjectData)
                .toList();

        if (updateOriginal) {
            projects.clear();
            projects.addAll(cleaned);
        }

        cleaned.forEach(projectData ->
                log.info("Cleaned Project [{}]: {} issues, {} versions downloaded.\n",
                        projectData.getProjectKey(),
                        projectData.getIssues().size(),
                        projectData.getVersions().size()));

        return cleaned;
    }

    // -------------------------------------------------------------------------

    private ProjectData cleanProjectData(ProjectData projectData) {
        List<Issue> validIssues = projectData.getIssues().stream()
                .filter(this::passesAllIssueChecks)
                .toList();

        List<Version> validVersions = projectData.getVersions().stream()
                .filter(this::passesAllVersionChecks)
                .toList();

        int removedIssues   = projectData.getIssues().size() - validIssues.size();
        int removedVersions = projectData.getVersions().size() - validVersions.size();

        log.info("Project [{}]: removed {} inconsistent issues, {} inconsistent versions.",
                projectData.getProjectKey(), removedIssues, removedVersions);

        return new ProjectData(projectData.getProjectKey(), validIssues, validVersions);
    }

    private boolean passesAllIssueChecks(Issue issue) {
        return issueChecks.stream().allMatch(check -> check.isValid(issue));
    }

    private boolean passesAllVersionChecks(Version version) {
        return versionChecks.stream().allMatch(check -> check.isValid(version));
    }
}