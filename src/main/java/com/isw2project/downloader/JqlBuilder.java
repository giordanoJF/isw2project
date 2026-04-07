package com.isw2project.downloader;

/**
 * Builds a JQL (Jira Query Language) string from a project key and filter map.
 *
 * Example output:
 *   project = "KAFKA" AND issuetype = "Bug" AND status = "Open"
 */
public class JqlBuilder {

    private JqlBuilder() {}

    /**
     * Builds a JQL query string.
     *
     * @param projectKey the Jira project key
     * @return JQL string
     */
    public static String build(String projectKey, String rawJql) {
        StringBuilder jql = new StringBuilder();
        jql.append("project = \"").append(projectKey).append("\"");

        if (rawJql != null && !rawJql.isBlank()) {
            jql.append(" AND ").append(rawJql);
        }

        jql.append(" ORDER BY created DESC");
        return jql.toString();
    }
}