package com.isw2project.csv;

import com.isw2project.model.Issue;
import com.isw2project.model.Version;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IssueCsvRowMapperService {

    public Map<String, String> map(Issue issue, Map<String, Boolean> enabledColumns) {
        Map<String, String> row = new LinkedHashMap<>();

        if (isEnabled(enabledColumns, "key"))
            row.put("key", issue.getKey());

        if (isEnabled(enabledColumns, "summary"))
            row.put("summary", issue.getFields().getSummary());

        if (isEnabled(enabledColumns, "created"))
            row.put("created", issue.getFields().getCreated());

        if (isEnabled(enabledColumns, "openingVersion"))
            row.put("openingVersion", formatVersion(issue.getFields().getOpeningVersion()));

        if (isEnabled(enabledColumns, "fixVersions"))
            row.put("fixVersions", formatVersionList(issue.getFields().getFixVersions()));

        if (isEnabled(enabledColumns, "affectedVersions"))
            row.put("affectedVersions", formatVersionList(issue.getFields().getAffectedVersions()));

        return row;
    }

    private boolean isEnabled(Map<String, Boolean> columns, String column) {
        return Boolean.TRUE.equals(columns.getOrDefault(column, false));
    }

    private String formatVersion(Version version) {
        if (version == null) return "";
        return version.getName();
    }

    private String formatVersionList(List<Version> versions) {
        if (versions == null || versions.isEmpty()) return "";
        return versions.stream()
                .map(Version::getName)
                .collect(Collectors.joining(";"));
    }
}