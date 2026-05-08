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

    public void applyProportion(List<ProjectData> projects) {
        projects.forEach(project -> {
            Optional<Double> p = proportionService.computeP(project);

            if (p.isEmpty()) {
                log.warn("Project [{}] could not compute P — no valid issues found.", project.getProjectKey());
                return;
            }

            log.info("Project [{}] computed P = {}.", project.getProjectKey(), p.get());
            applyToProject(project, p.get());
        });
    }

    private void applyToProject(ProjectData projectData, double p) {
        List<Version> orderedVersions = proportionService.getOrderedVersions(projectData.getVersions());

        long before = proportionService.countIssuesWithoutValidAV(projectData);
        projectData.getIssues().forEach(issue ->
                injectedVersionService.predictAndAssignAV(issue, orderedVersions, p, projectData.getProjectKey()));
        long after = proportionService.countIssuesWithoutValidAV(projectData);

        log.info("Project [{}] proportion applied: {} issues received predicted AV, {} could not be predicted.",
                projectData.getProjectKey(), before - after, after);
    }
}