package com.isw2project.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a Jira project version (release).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Version {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("released")
    private boolean released;

    @JsonProperty("releaseDate")
    private String releaseDate;

    public String getId()          { return id; }
    public String getName()        { return name; }
    public String getDescription() { return description; }
    public boolean isReleased()    { return released; }
    public String getReleaseDate() { return releaseDate; }

    @Override
    public String toString() {
        return String.format("Version{name='%s', released=%s, date='%s'}", name, released, releaseDate);
    }
}