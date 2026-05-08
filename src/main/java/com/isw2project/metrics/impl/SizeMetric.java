package com.isw2project.metrics.impl;

import com.isw2project.gitextractor.GitCommandException;
import com.isw2project.metrics.ClassMetricInput;
import com.isw2project.metrics.Metric;

import java.io.IOException;
import java.nio.file.Files;

/** Lines of code: counts non-blank lines in the saved source file. */
public class SizeMetric implements Metric {

    @Override
    public String columnName() { return "LOC"; }

    @Override
    public String compute(ClassMetricInput input) throws GitCommandException {
        try (var lines = Files.lines(input.getSnapshot().getCode())) {
            long count = lines.filter(line -> !line.isBlank()).count();
            return String.valueOf(count);
        } catch (IOException e) {
            throw new GitCommandException("Failed to read source file for LOC: " + input.getSnapshot().getCode(), e);
        }
    }
}