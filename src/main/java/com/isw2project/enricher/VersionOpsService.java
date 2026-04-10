package com.isw2project.enricher;

import com.isw2project.model.Issue;
import com.isw2project.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class VersionOpsService {

    private static final Logger log = LoggerFactory.getLogger(VersionOpsService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void assignOpeningVersion(Issue issue, List<Version> versions, String projectKey) {
        String created = issue.getFields().getCreated();
        if (created == null || created.isBlank()) {
            log.warn("Project [{}] issue [{}] has no creation date, skipping opening version.",
                    projectKey, issue.getKey());
            return;
        }

        Optional<Version> openingVersion = findOpeningVersion(created, versions);
        if (openingVersion.isPresent()) {
            issue.getFields().setOpeningVersion(openingVersion.get());
        } else {
            log.warn("Project [{}] issue [{}] has no opening version.", projectKey, issue.getKey());
        }
    }

    public void assignMostRecentFixVersion(Issue issue) {
        Optional<Version> mostRecent = findMostRecent(issue.getFields().getFixVersions());
        if (mostRecent.isPresent()) {
            issue.getFields().setFixVersions(List.of(mostRecent.get()));
        } else {
            log.warn("Issue [{}] has no fix version with a valid release date.", issue.getKey());
        }
    }

    private Optional<Version> findOpeningVersion(String issueCreatedDate, List<Version> versions) {
        LocalDate issueDate = LocalDate.parse(issueCreatedDate.substring(0, 10), FORMATTER);

        return versions.stream()
                .filter(v -> v.getReleaseDate() != null && !v.getReleaseDate().isBlank())
                .filter(v -> LocalDate.parse(v.getReleaseDate(), FORMATTER).isBefore(issueDate))
                .max(Comparator.comparing(v -> LocalDate.parse(v.getReleaseDate(), FORMATTER)));
    }

    private Optional<Version> findMostRecent(List<Version> versions) {
        if (versions == null || versions.isEmpty()) return Optional.empty();

        return versions.stream()
                .filter(v -> v.getReleaseDate() != null && !v.getReleaseDate().isBlank())
                .max(Comparator.comparing(v -> LocalDate.parse(v.getReleaseDate(), FORMATTER)));
    }
}