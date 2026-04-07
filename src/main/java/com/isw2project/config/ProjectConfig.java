package com.isw2project.config;

/**
 * Configuration for a single Jira project to download.
 */
public class ProjectConfig {

    private String key;
    private int maxResults = 100;
    private String jql;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

    public String getJql() { return jql; }
    public void setJql(String jql) { this.jql = jql; }
}



