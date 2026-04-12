package com.isw2project.consistency.checks;

import com.isw2project.consistency.IssueCheck;
import com.isw2project.model.Issue;
import com.isw2project.model.Version;

import java.time.LocalDate;
import java.util.List;

// remove lots of issues, so could be rearranged according to remove all the AV >= FV, without dropping the issue

public class IssueFixVersionAfterAffectedVersionCheck implements IssueCheck {

    @Override
    public boolean isValid(Issue issue) {
        List<Version> fixVersions      = issue.getFields().getFixVersions();
        List<Version> affectedVersions = issue.getFields().getAffectedVersions();

        if (fixVersions == null || fixVersions.isEmpty()) return false;
        if (fixVersions.getFirst().getReleaseDate() == null) return false;

        // if no AV, pass — proportion will predict IV later
        if (affectedVersions == null || affectedVersions.isEmpty()) return true;

        LocalDate fixDate = LocalDate.parse(fixVersions.getFirst().getReleaseDate());

        return affectedVersions.stream()
                .filter(v -> v.getReleaseDate() != null && !v.getReleaseDate().isBlank())
                .map(v -> LocalDate.parse(v.getReleaseDate()))
                .allMatch(fixDate::isAfter);
    }
}