package com.isw2project.enricher;

import com.isw2project.model.Issue;
import com.isw2project.model.ProjectData;
import com.isw2project.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class VersionService {

    private static final Logger log = LoggerFactory.getLogger(VersionService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void assignOpeningVersion(Issue issue, List<Version> versions, String projectKey) {
        String created = issue.getFields().getCreated();
        if (created == null || created.isBlank()) {
            log.warn("Project [{}] issue [{}] has no creation date",
                    projectKey, issue.getKey());
            return;
        }

        Optional<Version> openingVersion = findOpeningVersion(created, versions);
        if (openingVersion.isPresent()) {
            issue.getFields().setOpeningVersion(openingVersion.get());
        } else {
            //log.warn("Project [{}] issue [{}] has created date but no possible opening version.", projectKey, issue.getKey());
        }
    }

    private Optional<Version> findOpeningVersion(String issueCreatedDate, List<Version> versions) {
        LocalDate issueDate = LocalDate.parse(issueCreatedDate.substring(0, 10), FORMATTER);

        return versions.stream()
                .filter(v -> v.getReleaseDate() != null && !v.getReleaseDate().isBlank())
                .filter(v -> LocalDate.parse(v.getReleaseDate(), FORMATTER).isBefore(issueDate))
                .max(Comparator.comparing(v -> LocalDate.parse(v.getReleaseDate(), FORMATTER)));
    }

    public void assignMostRecentFixVersion(Issue issue) {
        List<Version> fixVersions = issue.getFields().getFixVersions();
        if (fixVersions == null || fixVersions.isEmpty()) {
            //log.warn("Issue [{}] has no fix versions", issue.getKey());
            return;
        }

        Optional<Version> mostRecent = findMostRecent(issue.getFields().getFixVersions());
        if (mostRecent.isPresent()) {
            issue.getFields().setFixVersions(List.of(mostRecent.get()));
        } else {
            log.warn("Issue [{}] has at least one fix version but with no valid release date.", issue.getKey());
        }
    }

    private Optional<Version> findMostRecent(List<Version> versions) {
        if (versions == null || versions.isEmpty()) return Optional.empty();

        return versions.stream()
                .filter(v -> v.getReleaseDate() != null && !v.getReleaseDate().isBlank())
                .max(Comparator.comparing(v -> LocalDate.parse(v.getReleaseDate(), FORMATTER)));
    }

    public void removeVersionsWithoutReleaseDate(Issue issue) {
        if (issue.getFields().getFixVersions() != null) {
            List<Version> cleaned = issue.getFields().getFixVersions().stream()
                    .filter(v -> v.getReleaseDate() != null && !v.getReleaseDate().isBlank())
                    .toList();
//            if (cleaned.size() < issue.getFields().getFixVersions().size())
//                log.warn("Issue [{}] had {} fix versions without release date removed.",
//                        issue.getKey(), issue.getFields().getFixVersions().size() - cleaned.size());
            issue.getFields().setFixVersions(cleaned);
        }

        if (issue.getFields().getAffectedVersions() != null) {
            List<Version> cleaned = issue.getFields().getAffectedVersions().stream()
                    .filter(v -> v.getReleaseDate() != null && !v.getReleaseDate().isBlank())
                    .toList();
//            if (cleaned.size() < issue.getFields().getAffectedVersions().size())
//                log.warn("Issue [{}] had {} affected versions without release date removed.",
//                        issue.getKey(), issue.getFields().getAffectedVersions().size() - cleaned.size());
            issue.getFields().setAffectedVersions(cleaned);
        }

        if (issue.getFields().getOpeningVersion() != null
                && (issue.getFields().getOpeningVersion().getReleaseDate() == null
                || issue.getFields().getOpeningVersion().getReleaseDate().isBlank())) {
            //log.warn("Issue [{}] opening version without release date removed.", issue.getKey());
            issue.getFields().setOpeningVersion(null);
        }
    }

    public void inferAffectedVersionsFromFixVersions(Issue issue) {
        List<Version> fixVersions = issue.getFields().getFixVersions();
        List<Version> affectedVersions = issue.getFields().getAffectedVersions();

        boolean hasNoAV = affectedVersions == null || affectedVersions.isEmpty()
                || affectedVersions.stream().allMatch(v -> v.getReleaseDate() == null || v.getReleaseDate().isBlank());

        if (!hasNoAV) return;

        List<Version> fixVersionsWithDate = fixVersions == null ? List.of() : fixVersions.stream()
                                                                              .filter(v -> v.getReleaseDate() != null && !v.getReleaseDate().isBlank())
                                                                              .toList();

        if (fixVersionsWithDate.size() <= 1) return;

        Optional<Version> mostRecent = findMostRecent(fixVersionsWithDate);
        if (mostRecent.isEmpty()) return;

        List<Version> inferred = fixVersionsWithDate.stream()
                .filter(v -> !v.equals(mostRecent.get()))
                .toList();

        issue.getFields().setFixVersions(List.of(mostRecent.get()));
        issue.getFields().setAffectedVersions(inferred);
    }

    public int removeZombieVersionReferences(Issue issue, List<Version> projectVersions) {
        int removed = 0;

        if (issue.getFields().getFixVersions() != null) {
            int before = issue.getFields().getFixVersions().size();
            issue.getFields().setFixVersions(
                    issue.getFields().getFixVersions().stream()
                            .filter(projectVersions::contains)
                            .toList()
            );
            removed += before - issue.getFields().getFixVersions().size();
        }

        if (issue.getFields().getAffectedVersions() != null) {
            int before = issue.getFields().getAffectedVersions().size();
            issue.getFields().setAffectedVersions(
                    issue.getFields().getAffectedVersions().stream()
                            .filter(projectVersions::contains)
                            .toList()
            );
            removed += before - issue.getFields().getAffectedVersions().size();
        }

        if (issue.getFields().getOpeningVersion() != null
                && !projectVersions.contains(issue.getFields().getOpeningVersion())) {
            issue.getFields().setOpeningVersion(null);
            removed++;
        }

        return removed;
    }

    public int removeZombieVersionReferences(ProjectData project) {
        return project.getIssues().stream()
                .mapToInt(issue -> removeZombieVersionReferences(issue, project.getVersions()))
                .sum();
    }
}