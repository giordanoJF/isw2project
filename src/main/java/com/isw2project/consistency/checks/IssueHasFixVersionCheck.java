package com.isw2project.consistency.checks;

import com.isw2project.consistency.IssueCheck;
import com.isw2project.model.Issue;

public class IssueHasFixVersionCheck implements IssueCheck {

    @Override
    public boolean isValid(Issue issue) {
        return issue.getFields().getFixVersions() != null
                && !issue.getFields().getFixVersions().isEmpty();
    }
}