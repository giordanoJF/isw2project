package com.isw2project.refactoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public class ClassSizeFilterService {

    private static final Logger log = LoggerFactory.getLogger(ClassSizeFilterService.class);

    public LinkedHashMap<String, Integer> filter(LinkedHashMap<String, Integer> smells, Path sourceDir, int locThreshold) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        int removed = 0;
        for (Map.Entry<String, Integer> entry : smells.entrySet()) {
            long loc = countLines(sourceDir.resolve(entry.getKey()));
            if (loc >= locThreshold) {
                result.put(entry.getKey(), entry.getValue());
            } else {
                removed++;
            }
        }
        log.info("Removed {} classes with LOC < {} (kept: {})", removed, locThreshold, result.size());
        return result;
    }

    long countLines(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
