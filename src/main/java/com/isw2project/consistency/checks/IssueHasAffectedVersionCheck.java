package com.isw2project.consistency.checks;

import com.isw2project.consistency.IssueCheck;
import com.isw2project.model.Issue;

//694 total issues
//571 used to compute P (have valid IV, FV, OV, and FV ≠ OV)
//123 excluded from P, split into:
//
//        122 with no valid AV → proportion candidates
//1 with valid AV but FV == OV (OPENJPA-2480) → excluded from P but not a proportion candidate
//
//
//Of the 122 proportion candidates:
//
//        121 successfully received predicted AV
//1 failed (predicted IV == FV, subList empty)
//
//
//Final result: 1 issue with no valid AV remaining → removed by checkIssueHasAffectedVersion

public class IssueHasAffectedVersionCheck implements IssueCheck {

    @Override
    public boolean isValid(Issue issue) {
        return issue.getFields().getAffectedVersions() != null
                && !issue.getFields().getAffectedVersions().isEmpty();
    }
}