package com.isw2project.consistency.checks;

import com.isw2project.consistency.IssueCheck;
import com.isw2project.model.Issue;
import com.isw2project.model.Version;

import java.time.LocalDate;
import java.util.List;

public class IssueCreatedAfterOldestVersionCheck implements IssueCheck {

    private final LocalDate oldestVersionDate;

    public IssueCreatedAfterOldestVersionCheck(List<Version> versions) {
        this.oldestVersionDate = versions.stream()
                .filter(v -> v.getReleaseDate() != null && !v.getReleaseDate().isBlank())
                .map(v -> LocalDate.parse(v.getReleaseDate()))
                .min(LocalDate::compareTo)
                .orElse(LocalDate.MIN);
    }

    @Override
    public boolean isValid(Issue issue) {
        String created = issue.getFields().getCreated();
        if (created == null || created.isBlank()) return false;
        return LocalDate.parse(created.substring(0, 10)).isAfter(oldestVersionDate);
    }
}