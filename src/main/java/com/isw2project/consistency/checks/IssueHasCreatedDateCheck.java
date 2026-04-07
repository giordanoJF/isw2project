package com.isw2project.consistency.checks;

import com.isw2project.consistency.IssueCheck;
import com.isw2project.model.Issue;

public class IssueHasCreatedDateCheck implements IssueCheck {
    @Override
    public boolean isValid(Issue issue) {
        return issue.getFields() != null
                && issue.getFields().getCreated() != null
                && !issue.getFields().getCreated().isBlank();
    }
}