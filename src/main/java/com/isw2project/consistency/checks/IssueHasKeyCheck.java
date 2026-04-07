package com.isw2project.consistency.checks;

import com.isw2project.consistency.IssueCheck;
import com.isw2project.model.Issue;

public class IssueHasKeyCheck implements IssueCheck {
    @Override
    public boolean isValid(Issue issue) {
        return issue.getKey() != null && !issue.getKey().isBlank();
    }
}