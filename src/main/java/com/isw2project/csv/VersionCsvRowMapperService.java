package com.isw2project.csv;

import com.isw2project.model.Version;

import java.util.LinkedHashMap;
import java.util.Map;

public class VersionCsvRowMapperService {

    public Map<String, String> map(Version version, Map<String, Boolean> enabledColumns) {
        Map<String, String> row = new LinkedHashMap<>();

        if (isEnabled(enabledColumns, "name"))
            row.put("name", version.getName());

        if (isEnabled(enabledColumns, "releaseDate"))
            row.put("releaseDate", version.getReleaseDate());

        if (isEnabled(enabledColumns, "released"))
            row.put("released", String.valueOf(version.isReleased()));

        return row;
    }

    private boolean isEnabled(Map<String, Boolean> columns, String column) {
        return Boolean.TRUE.equals(columns.getOrDefault(column, false));
    }
}