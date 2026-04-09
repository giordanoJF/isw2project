package com.isw2project.consistency;

import com.isw2project.model.Issue;
import com.isw2project.model.ProjectData;
import com.isw2project.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ConsistencyOrchestrator {

    private final List<IssueCheck> issueChecks;
    private final List<VersionCheck> versionChecks;
    private static final Logger log = LoggerFactory.getLogger(ConsistencyOrchestrator.class);
    private final List<Function<List<Version>, IssueCheck>> perProjectIssueChecks;

    public ConsistencyOrchestrator(List<IssueCheck> issueChecks,
                              List<VersionCheck> versionChecks,
                              List<Function<List<Version>, IssueCheck>> perProjectIssueChecks) {
        this.issueChecks           = List.copyOf(issueChecks);
        this.versionChecks         = List.copyOf(versionChecks);
        this.perProjectIssueChecks = List.copyOf(perProjectIssueChecks);
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
        List<IssueCheck> allIssueChecks = new ArrayList<>(issueChecks);
        perProjectIssueChecks.stream()
                .map(factory -> factory.apply(projectData.getVersions()))
                .forEach(allIssueChecks::add);

        List<Issue> validIssues = projectData.getIssues().stream()
                .filter(issue -> allIssueChecks.stream().allMatch(check -> check.isValid(issue)))
                .toList();

        List<Version> validVersions = projectData.getVersions().stream()
                .filter(version -> versionChecks.stream().allMatch(check -> check.isValid(version)))
                .toList();

        log.info("Project [{}]: removed {} inconsistent issues, {} inconsistent versions.",
                projectData.getProjectKey(),
                projectData.getIssues().size() - validIssues.size(),
                projectData.getVersions().size() - validVersions.size());

        return new ProjectData(projectData.getProjectKey(), validIssues, validVersions);
    }

}