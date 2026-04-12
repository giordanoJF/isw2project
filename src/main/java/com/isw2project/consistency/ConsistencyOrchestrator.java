package com.isw2project.consistency;

import com.isw2project.consistency.checks.*;
import com.isw2project.model.Issue;
import com.isw2project.model.ProjectData;
import com.isw2project.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;

public class ConsistencyOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ConsistencyOrchestrator.class);

    public List<ProjectData> checkIssueHasKey(List<ProjectData> projects) {
        return applyIssueCheck(projects, new IssueHasKeyCheck());
    }

    public List<ProjectData> checkIssueHasCreatedDate(List<ProjectData> projects) {
        return applyIssueCheck(projects, new IssueHasCreatedDateCheck());
    }

    public List<ProjectData> checkIssueHasFixVersion(List<ProjectData> projects) {
        return applyIssueCheck(projects, new IssueHasFixVersionCheck());
    }

    public List<ProjectData> checkIssueFixVersionHasReleaseDate(List<ProjectData> projects) {
        return applyIssueCheck(projects, new IssueFixVersionHasReleaseDateCheck());
    }

    public List<ProjectData> checkIssueFixVersionAfterOpeningVersion(List<ProjectData> projects) {
        return applyIssueCheck(projects, new IssueFixVersionAfterEqualOpeningVersionCheck());
    }

    public List<ProjectData> checkIssueCreatedAfterOldestVersion(List<ProjectData> projects) {
        // per-project check — each project uses its own version list
        return applyPerProjectIssueCheck(projects,
                IssueCreatedAfterOldestVersionCheck::new);
    }

    public List<ProjectData> checkVersionHasName(List<ProjectData> projects) {
        return applyVersionCheck(projects, new VersionHasNameCheck());
    }

    public List<ProjectData> checkVersionIsReleased(List<ProjectData> projects) {
        return applyVersionCheck(projects, new VersionIsReleasedCheck());
    }

    public List<ProjectData> checkVersionHasReleaseDate(List<ProjectData> projects) {
        return applyVersionCheck(projects, new VersionHasReleaseDateCheck());
    }

    public List<ProjectData> checkIssueFixVersionAfterAffectedVersion(List<ProjectData> projects) {
        return applyIssueCheck(projects, new IssueFixVersionAfterAffectedVersionCheck());
    }

    // -------------------------------------------------------------------------

    private List<ProjectData> applyIssueCheck(List<ProjectData> projects, IssueCheck check) {
        return projects.stream()
                .map(p -> {
                    List<Issue> valid = p.getIssues().stream()
                            .filter(check::isValid)
                            .toList();
                    logIssueCheck(p.getProjectKey(), check.getClass().getSimpleName(),
                            p.getIssues().size() - valid.size(), valid.size());
                    return new ProjectData(p.getProjectKey(), valid, p.getVersions());
                })
                .toList();
    }

    private List<ProjectData> applyPerProjectIssueCheck(List<ProjectData> projects,
                                                        Function<List<Version>, IssueCheck> factory) {
        return projects.stream()
                .map(p -> {
                    IssueCheck check = factory.apply(p.getVersions());
                    List<Issue> valid = p.getIssues().stream()
                            .filter(check::isValid)
                            .toList();
                    logIssueCheck(p.getProjectKey(), check.getClass().getSimpleName(),
                            p.getIssues().size() - valid.size(), valid.size());
                    return new ProjectData(p.getProjectKey(), valid, p.getVersions());
                })
                .toList();
    }

    private List<ProjectData> applyVersionCheck(List<ProjectData> projects, VersionCheck check) {
        return projects.stream()
                .map(p -> {
                    List<Version> valid = p.getVersions().stream()
                            .filter(check::isValid)
                            .toList();
                    logVersionCheck(p.getProjectKey(), check.getClass().getSimpleName(),
                            p.getVersions().size() - valid.size(), valid.size());
                    return new ProjectData(p.getProjectKey(), p.getIssues(), valid);
                })
                .toList();
    }

    private void logIssueCheck(String projectKey, String checkName, int removed, int remaining) {
        log.info("Project [{}] issue check [{}]: removed {} issues ({} remaining).",
                projectKey, checkName, removed, remaining);
    }

    private void logVersionCheck(String projectKey, String checkName, int removed, int remaining) {
        log.info("Project [{}] version check [{}]: removed {} versions ({} remaining).",
                projectKey, checkName, removed, remaining);
    }
}