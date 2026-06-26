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

    public Map<String, Integer> filter(Map<String, Integer> smells, Path sourceDir,
                                       int locThreshold, int minSmellsThreshold) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        int removedLoc = 0;
        int removedInterface = 0;
        int removedSmells = 0;
        for (Map.Entry<String, Integer> entry : smells.entrySet()) {
            Path file = sourceDir.resolve(entry.getKey());
            long loc = countLines(file);
            if (loc < locThreshold) {
                removedLoc++;
            } else if (isNonInstantiable(file)) {
                removedInterface++;
            } else if (entry.getValue() < minSmellsThreshold) {
                removedSmells++;
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        log.info("Removed {} classes with LOC < {}, {} interfaces/abstract, {} with Nsmells < {} (kept: {})",
                removedLoc, locThreshold, removedInterface, removedSmells, minSmellsThreshold, result.size());
        return result;
    }

    long countLines(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean isNonInstantiable(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.anyMatch(line -> {
                String t = line.stripLeading();
                return t.startsWith("public interface ")
                    || t.startsWith("interface ")
                    || t.startsWith("public sealed interface ")
                    || t.startsWith("public non-sealed interface ")
                    || t.startsWith("public abstract class ")
                    || t.startsWith("abstract class ");
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
