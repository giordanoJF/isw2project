package com.isw2project.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a Jira issue.
 * Only the fields we care about are mapped; extras are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Issue {

    @JsonProperty("id")
    private String id;

    @JsonProperty("key")
    private String key;

    @JsonProperty("fields")
    private Fields fields;

    public String getId() { return id; }
    public String getKey() { return key; }
    public Fields getFields() { return fields; }

    @Override
    public String toString() {
        return String.format("Issue{key='%s', summary='%s'}", key,
                fields != null ? fields.getSummary() : "N/A");
    }

    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fields {

        @JsonProperty("summary")
        private String summary;

        @JsonProperty("status")
        private Status status;

        @JsonProperty("issuetype")
        private IssueType issueType;

        @JsonProperty("priority")
        private Priority priority;

        @JsonProperty("created")
        private String created;

        @JsonProperty("updated")
        private String updated;

        @JsonProperty("versions")
        private List<Version> affectedVersions;

        @JsonProperty("fixVersions")
        private List<Version> fixVersions;

        public String getSummary()    { return summary; }
        public Status getStatus()     { return status; }
        public IssueType getIssueType() { return issueType; }
        public Priority getPriority() { return priority; }
        public String getCreated()    { return created; }
        public String getUpdated()    { return updated; }
        public List<Version> getAffectedVersions() { return affectedVersions; }
        public List<Version> getFixVersions() { return fixVersions; }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Status {
        @JsonProperty("name")
        private String name;
        public String getName() { return name; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IssueType {
        @JsonProperty("name")
        private String name;
        public String getName() { return name; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Priority {
        @JsonProperty("name")
        private String name;
        public String getName() { return name; }
    }

}