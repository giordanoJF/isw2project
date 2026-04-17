package com.isw2project.enricher;

import com.isw2project.model.ProjectData;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnricherOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(EnricherOrchestrator.class);

    private final VersionService versionService;

    public EnricherOrchestrator(VersionService versionService) {
        this.versionService = versionService;
    }

    public List<ProjectData> enrichWithOV(List<ProjectData> projects) {
        log.info("Starting opening version enrichment");

        return projects.stream()
                .map(p -> {
                    p.getIssues().forEach(issue ->
                            versionService.assignOpeningVersion(issue, p.getVersions(), p.getProjectKey()));

                    long success = p.getIssues().stream()
                            .filter(issue -> issue.getFields().getOpeningVersion() != null)
                            .count();
                    log.info("Project [{}] opening version enrichment: {} enriched, {} not enriched.\n",
                            p.getProjectKey(), success, p.getIssues().size()-success);
                    return p;
                })
                .toList();
    }

    public List<ProjectData> enrichMultiToSingleFV(List<ProjectData> projects) {
        log.info("Starting multiple FV to single one enrichment");

        return projects.stream()
                .map(p -> {
                    long alreadySingle = p.getIssues().stream()
                            .filter(issue -> issue.getFields().getFixVersions() != null
                                    && issue.getFields().getFixVersions().size() == 1)
                            .count();

                    p.getIssues().forEach(versionService::assignMostRecentFixVersion);

                    long afterSingle = p.getIssues().stream()
                            .filter(issue -> issue.getFields().getFixVersions() != null
                                    && issue.getFields().getFixVersions().size() == 1
                                    && issue.getFields().getFixVersions().getFirst().getReleaseDate() != null)
                            .count();

                    long enriched = afterSingle - alreadySingle;
                    log.info("Project [{}] multiple FV to single one enrichment: {} enriched, {} not enriched.\n",
                            p.getProjectKey(), enriched, p.getIssues().size() - enriched);
                    return p;
                })
                .toList();
    }

    public List<ProjectData> cleanIssueVersionsWithoutReleaseDate(List<ProjectData> projects) {
        log.info("Starting version date cleaning");

        return projects.stream()
                .map(p -> {
                    long affected = p.getIssues().stream()
                            .filter(issue -> {
                                int fvBefore = issue.getFields().getFixVersions() != null
                                        ? issue.getFields().getFixVersions().size() : 0;
                                int avBefore = issue.getFields().getAffectedVersions() != null
                                        ? issue.getFields().getAffectedVersions().size() : 0;

                                versionService.removeVersionsWithoutReleaseDate(issue);

                                int fvAfter = issue.getFields().getFixVersions() != null
                                        ? issue.getFields().getFixVersions().size() : 0;
                                int avAfter = issue.getFields().getAffectedVersions() != null
                                        ? issue.getFields().getAffectedVersions().size() : 0;

                                return fvAfter < fvBefore || avAfter < avBefore;
                            })
                            .count();

                    log.info("Project [{}] version date cleaning: {} issues had at least one version removed.\n",
                            p.getProjectKey(), affected);
                    return p;
                })
                .toList();
    }

    public List<ProjectData> enrichAVFromFV(List<ProjectData> projects) {
        log.info("Starting AV inference from FV enrichment.");

        return projects.stream()
                .map(p -> {
                    long before = p.getIssues().stream()
                            .filter(issue -> issue.getFields().getAffectedVersions() != null
                                    && !issue.getFields().getAffectedVersions().isEmpty()
                                    && issue.getFields().getAffectedVersions().stream()
                                    .anyMatch(v -> v.getReleaseDate() != null && !v.getReleaseDate().isBlank()))
                            .count();

                    p.getIssues().forEach(versionService::inferAffectedVersionsFromFixVersions);

                    long after = p.getIssues().stream()
                            .filter(issue -> issue.getFields().getAffectedVersions() != null
                                    && !issue.getFields().getAffectedVersions().isEmpty()
                                    && issue.getFields().getAffectedVersions().stream()
                                    .anyMatch(v -> v.getReleaseDate() != null && !v.getReleaseDate().isBlank()))
                            .count();

                    log.info("Project [{}] AV inference from FV: {} issues enriched, {} issues not enriched by this method.\n",
                            p.getProjectKey(), after - before, p.getIssues().size() - (after - before));
                    return p;
                })
                .toList();
    }

    public List<ProjectData> removeZombieVersionReferences(List<ProjectData> projects) {
        log.info("Starting zombie version reference removal for {} projects.", projects.size());

        projects.forEach(p -> {
            int totalRemoved = versionService.removeZombieVersionReferences(p);
            log.info("Project [{}] zombie version removal: {} version references removed across all issues.\n",
                    p.getProjectKey(), totalRemoved);
        });

        return projects;
    }
}