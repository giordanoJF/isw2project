package com.isw2project.csv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class CsvWriterService {

    private static final Logger log = LoggerFactory.getLogger(CsvWriterService.class);
    private static final String SEPARATOR = ",";

    public void write(String outputDir, String filename, List<Map<String, String>> rows) {
        if (rows.isEmpty()) {
            log.warn("No rows to write for file: {}", filename);
            return;
        }

        Path dirPath = Paths.get(outputDir);
        Path filePath = dirPath.resolve(filename);

        try {
            Files.createDirectories(dirPath);
            try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
                writer.write(buildHeader(rows.get(0)));
                writer.newLine();
                for (Map<String, String> row : rows) {
                    writer.write(buildRow(row));
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write CSV file: " + filePath, e);
        }
    }

    private String buildHeader(Map<String, String> firstRow) {
        return String.join(SEPARATOR, firstRow.keySet());
    }

    private String buildRow(Map<String, String> row) {
        StringJoiner joiner = new StringJoiner(SEPARATOR);
        row.values().forEach(value -> joiner.add(escapeValue(value)));
        return joiner.toString();
    }

    private String escapeValue(String value) {
        if (value == null) return "";
        if (value.contains(SEPARATOR) || value.contains("\"") || value.contains("\n"))
            return "\"" + value.replace("\"", "\"\"") + "\"";
        return value;
    }
}