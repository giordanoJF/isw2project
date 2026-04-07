package com.isw2project.consistency;

import com.isw2project.model.Issue;

public interface IssueCheck {
    boolean isValid(Issue issue);
}