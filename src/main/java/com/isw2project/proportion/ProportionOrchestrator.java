package com.isw2project.proportion;

import com.isw2project.model.ProjectData;
import com.isw2project.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class ProportionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProportionOrchestrator.class);

    private final ProportionService proportionService;
    private final InjectedVersionService injectedVersionService;

    public ProportionOrchestrator(ProportionService proportionService,
                                  InjectedVersionService injectedVersionService) {
        this.proportionService      = proportionService;
        this.injectedVersionService = injectedVersionService;
    }

    /**
     * Computes P from the given input (can be cleaned or original dataset)
     * then applies proportion to predict AV for issues without valid AV
     * in the target dataset.
     */
    public void applyProportion(List<ProjectData> pComputationInput, List<ProjectData> target) {
        // some will be skipped because their predicted IV land on the same index as FV,
        // and others will have 0 at denominator
        pComputationInput.forEach(inputProject -> {
            Optional<Double> p = proportionService.computeP(inputProject);

            if (p.isEmpty()) {
                log.warn("Project [{}] could not compute P — no valid issues found.", inputProject.getProjectKey());
                return;
            }

            log.info("Project [{}] computed P = {}.", inputProject.getProjectKey(), p.get());

            target.stream()
                    .filter(targetProject -> targetProject.getProjectKey().equals(inputProject.getProjectKey()))
                    .findFirst()
                    .ifPresent(targetProject -> applyToProject(targetProject, p.get()));
        });
    }

    private void applyToProject(ProjectData projectData, double p) {
        List<Version> orderedVersions = proportionService.getOrderedVersions(projectData.getVersions());

        long before = countIssuesWithoutValidAV(projectData);

        projectData.getIssues().forEach(issue ->
                injectedVersionService.predictAndAssignAV(issue, orderedVersions, p, projectData.getProjectKey()));

        long after = countIssuesWithoutValidAV(projectData);

        log.info("Project [{}] proportion applied: {} issues received predicted AV.\n",
                projectData.getProjectKey(), before - after);
    }

    private long countIssuesWithoutValidAV(ProjectData projectData) {
        return projectData.getIssues().stream()
                .filter(issue -> issue.getFields().getAffectedVersions() == null
                        || issue.getFields().getAffectedVersions().isEmpty()
                        || issue.getFields().getAffectedVersions().stream()
                        .allMatch(v -> v.getReleaseDate() == null || v.getReleaseDate().isBlank()))
                .count();
    }
}