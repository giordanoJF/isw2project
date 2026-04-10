package com.isw2project.consistency.checks;

import com.isw2project.consistency.IssueCheck;
import com.isw2project.model.Issue;

public class IssueFixVersionHasReleaseDateCheck implements IssueCheck {

    @Override
    public boolean isValid(Issue issue) {
        if (issue.getFields().getFixVersions() == null || issue.getFields().getFixVersions().isEmpty())
            return false;

        return issue.getFields().getFixVersions().stream()
                .anyMatch(v -> v.getReleaseDate() != null && !v.getReleaseDate().isBlank());
    }
}