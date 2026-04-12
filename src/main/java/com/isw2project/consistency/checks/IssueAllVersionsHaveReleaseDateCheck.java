package com.isw2project.consistency.checks;

import com.isw2project.consistency.IssueCheck;
import com.isw2project.model.Issue;
import com.isw2project.model.Version;

import java.util.List;

public class IssueAllVersionsHaveReleaseDateCheck implements IssueCheck {

    @Override
    public boolean isValid(Issue issue) {
        Version ov = issue.getFields().getOpeningVersion();
        List<Version> fv = issue.getFields().getFixVersions();
        List<Version> av = issue.getFields().getAffectedVersions();

        if (ov != null && (ov.getReleaseDate() == null || ov.getReleaseDate().isBlank())) return false;
        if (fv != null && fv.stream().anyMatch(v -> v.getReleaseDate() == null || v.getReleaseDate().isBlank())) return false;

        return av == null || av.stream().allMatch(v -> v.getReleaseDate() != null && !v.getReleaseDate().isBlank());
    }
}