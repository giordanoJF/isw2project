package com.isw2project.csv;

import com.isw2project.model.JavaClassSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps a JavaClassSnapshot to a row compatible with CsvWriterService.
 * Uses LinkedHashMap to preserve column order: java_class_path, release.
 * Follows the same pattern as IssueCsvRowMapperService and VersionCsvRowMapperService.
 */
public class SnapshotCsvRowMapperService {

    public Map<String, String> map(JavaClassSnapshot snapshot) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("java_class_path", snapshot.getClassPath());
        row.put("release", snapshot.getRelease());
        return row;
    }
}