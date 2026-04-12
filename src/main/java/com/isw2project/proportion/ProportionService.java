package com.isw2project.proportion;

import com.isw2project.model.Issue;
import com.isw2project.model.ProjectData;
import com.isw2project.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ProportionService {

    private static final Logger log = LoggerFactory.getLogger(ProportionService.class);

    /**
     * Computes P for a project as the average of (FV - IV) / (FV - OV)
     * across all issues that have IV, FV and OV with valid release dates.
     * Returns empty if no valid issues are found.
     */
    public Optional<Double> computeP(ProjectData projectData) {
        List<Version> orderedVersions = getOrderedVersions(projectData.getVersions());

        List<Double> proportions = projectData.getIssues().stream()
                .map(issue -> computeIssueP(issue, orderedVersions))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        if (proportions.isEmpty()) return Optional.empty();

        log.info("Project [{}] P computed on {} issues out of {}.",
                projectData.getProjectKey(), proportions.size(), projectData.getIssues().size());

        double p = proportions.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        return Optional.of(p);
    }

    private Optional<Double> computeIssueP(Issue issue, List<Version> orderedVersions) {
        Version ov = issue.getFields().getOpeningVersion();
        Version fv = issue.getFields().getFixVersions() == null || issue.getFields().getFixVersions().isEmpty()
                ? null : issue.getFields().getFixVersions().getFirst();
        Version iv = getOldestAffectedVersion(issue.getFields().getAffectedVersions(), orderedVersions);

        if (ov == null || fv == null || iv == null) return Optional.empty();
        if (ov.getReleaseDate() == null || fv.getReleaseDate() == null) return Optional.empty();

        int ovIndex = indexOf(ov, orderedVersions);
        int fvIndex = indexOf(fv, orderedVersions);
        int ivIndex = indexOf(iv, orderedVersions);

        if (ovIndex < 0 || fvIndex < 0 || ivIndex < 0) return Optional.empty();
        if (fvIndex == ovIndex) return Optional.empty();

        double p = (double) (fvIndex - ivIndex) / (fvIndex - ovIndex);
        return Optional.of(p);
    }

    Version getOldestAffectedVersion(List<Version> affectedVersions, List<Version> orderedVersions) {
        if (affectedVersions == null || affectedVersions.isEmpty()) return null;

        return affectedVersions.stream()
                .filter(v -> v.getReleaseDate() != null && !v.getReleaseDate().isBlank())
                .min(Comparator.comparingInt(v -> indexOf(v, orderedVersions)))
                .orElse(null);
    }

    int indexOf(Version version, List<Version> orderedVersions) {
        for (int i = 0; i < orderedVersions.size(); i++) {
            if (orderedVersions.get(i).equals(version)) return i;
        }
        return -1;
    }

    List<Version> getOrderedVersions(List<Version> versions) {
        return versions.stream()
                .filter(v -> v.getReleaseDate() != null && !v.getReleaseDate().isBlank())
                .sorted(Comparator.comparing(v -> LocalDate.parse(v.getReleaseDate())))
                .toList();
    }
}