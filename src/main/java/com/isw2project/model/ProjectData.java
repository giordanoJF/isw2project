package com.isw2project.model;

import java.util.Collections;
import java.util.List;

/**
 * Holds all downloaded data for a single Jira project.
 * This is the main data structure that will be passed around and enriched in future steps.
 */
public class ProjectData {

    private final String projectKey;
    private final List<Issue> issues;
    private final List<Version> versions;

    public ProjectData(String projectKey, List<Issue> issues, List<Version> versions) {
        this.projectKey = projectKey;
        this.issues     = Collections.unmodifiableList(issues);
        this.versions   = Collections.unmodifiableList(versions);
    }

    public String getProjectKey()   { return projectKey; }
    public List<Issue> getIssues()  { return issues; }
    public List<Version> getVersions() { return versions; }
}