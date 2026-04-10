package com.isw2project.consistency.checks;

import com.isw2project.consistency.IssueCheck;
import com.isw2project.model.Issue;
import com.isw2project.model.Version;

import java.time.LocalDate;
import java.util.List;

public class IssueFixVersionAfterOpeningVersionCheck implements IssueCheck {

    @Override
    public boolean isValid(Issue issue) {
        // pass if just one fix version is after the opening
        // we need the OV to be enriched
        Version openingVersion = issue.getFields().getOpeningVersion();
        List<Version> fixVersions = issue.getFields().getFixVersions();

        if (openingVersion == null || openingVersion.getReleaseDate() == null) return false;
        if (fixVersions == null || fixVersions.isEmpty()) return false;

        LocalDate openingDate = LocalDate.parse(openingVersion.getReleaseDate());

        return fixVersions.stream()
                .filter(v -> v.getReleaseDate() != null && !v.getReleaseDate().isBlank())
                .map(v -> LocalDate.parse(v.getReleaseDate()))
                .anyMatch(fixDate -> !fixDate.isBefore(openingDate));
    }
}