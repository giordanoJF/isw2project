package com.isw2project.enricher;

import com.isw2project.model.Version;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class VersionDateService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public Optional<Version> findClosestVersionBefore(String issueDateStr, List<Version> versions) {
        LocalDate issueDate = LocalDate.parse(issueDateStr.substring(0, 10), FORMATTER);

        return versions.stream()
                .filter(v -> v.getReleaseDate() != null && !v.getReleaseDate().isBlank())
                .filter(v -> LocalDate.parse(v.getReleaseDate(), FORMATTER).isBefore(issueDate))
                .max(Comparator.comparing(v -> LocalDate.parse(v.getReleaseDate(), FORMATTER)));
    }
}