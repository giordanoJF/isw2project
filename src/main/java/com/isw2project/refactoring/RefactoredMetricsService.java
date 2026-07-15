package com.isw2project.refactoring;

import com.isw2project.gitextractor.support.GitLogStatsService;
import com.isw2project.metrics.ClassMetricInput;
import com.isw2project.metrics.MetricsComputerService;
import com.isw2project.metrics.impl.*;
import com.isw2project.model.JavaClassSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;

public class RefactoredMetricsService {

    private static final Logger log = LoggerFactory.getLogger(RefactoredMetricsService.class);

    private static final String GIT_REF = "4.1.1";
    private static final String JAVA_EXT = ".java";

    private record ClassDescriptor(String simpleName, String gitPath, List<String> variants) {}

    private static final List<ClassDescriptor> CLASSES = List.of(
        new ClassDescriptor("BrokerImpl",
            "openjpa-kernel/src/main/java/org/apache/openjpa/kernel/BrokerImpl.java",
            List.of("C1", "C2", "C3", "C4")),
        new ClassDescriptor("LoginDialog",
            "openjpa-examples/opentrader/src/main/java/org/apache/openjpa/trader/client/LoginDialog.java",
            List.of("C1", "C2"))
    );

    private final File repoDir;
    private final Path refactoredClassesDir;
    private final NsmellsService nsmellsService;
    private final int batchSize;
    private final double cpuFraction;

    public RefactoredMetricsService(File repoDir, Path refactoredClassesDir,
                                    NsmellsService nsmellsService,
                                    int batchSize, double cpuFraction) {
        this.repoDir = repoDir;
        this.refactoredClassesDir = refactoredClassesDir;
        this.nsmellsService = nsmellsService;
        this.batchSize = batchSize;
        this.cpuFraction = cpuFraction;
    }

    @SuppressWarnings("java:S5443")
    public List<Map<String, String>> compute() {
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("m4_c0_");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            return doCompute(tempDir);
        } finally {
            deleteTempDir(tempDir);
        }
    }

    private List<Map<String, String>> doCompute(Path tempDir) {
        // Extract C_0 files from git to tempDir
        for (ClassDescriptor desc : CLASSES) {
            extractFromGit(desc.gitPath(), tempDir.resolve(desc.simpleName() + JAVA_EXT));
        }

        GitLogStatsService gitLogStats = new GitLogStatsService(repoDir);
        LocalDate releaseDate = resolveReleaseDate();

        // All computable process metrics (Nfix excluded: requires Jira data not available in M4)
        MetricsComputerService computer = new MetricsComputerService(List.of(
            new SizeMetric(),
            new LOCTouchedMetric(gitLogStats),
            new NrMetric(gitLogStats),
            new NauthMetric(gitLogStats),
            new LOCAddedMetric(gitLogStats),
            new MaxLOCAddedMetric(gitLogStats),
            new AvgLOCAddedMetric(gitLogStats),
            new ChurnMetric(gitLogStats),
            new MaxChurnMetric(gitLogStats),
            new AvgChurnMetric(gitLogStats),
            new ChangeSetSizeMetric(gitLogStats),
            new MaxChangeSetMetric(gitLogStats),
            new AvgChangeSetMetric(gitLogStats),
            new AgeMetric(gitLogStats),
            new WeightedAgeMetric(gitLogStats),
            new ExpMetric(gitLogStats),
            new SexpMetric(gitLogStats)
        ));

        // PMD: C_0 files (both in tempDir in a single batch) and all C_X files at once
        Map<String, Integer> c0Smells = nsmellsService.computeSmells(tempDir, batchSize, cpuFraction);
        Map<String, Integer> cxSmells = nsmellsService.computeSmells(refactoredClassesDir, batchSize, cpuFraction);

        List<Map<String, String>> rows = new ArrayList<>();

        for (ClassDescriptor desc : CLASSES) {
            Path c0File = tempDir.resolve(desc.simpleName() + JAVA_EXT);
            JavaClassSnapshot c0Snap = new JavaClassSnapshot(desc.gitPath(), GIT_REF, c0File);
            Map<String, String> c0Computed = computer.compute(new ClassMetricInput(c0Snap, GIT_REF, releaseDate));

            int c0NSmells = c0Smells.getOrDefault(desc.simpleName() + JAVA_EXT, 0);
            rows.add(buildRow(desc.simpleName(), "C0", c0Computed, c0NSmells));

            String fileName = Path.of(desc.gitPath()).getFileName().toString();

            for (String variant : desc.variants()) {
                Path cxFile = refactoredClassesDir.resolve(desc.simpleName()).resolve(variant).resolve(fileName);
                if (!Files.exists(cxFile)) {
                    log.warn("Skipping {} {} — file not found: {}", desc.simpleName(), variant, cxFile);
                    continue;
                }

                // Full recomputation for C_X: same classPath (git history) but different codeFilePath (refactored source).
                // Process metrics (NR, Churn, Age, ...) query git by classPath → identical to C_0 by construction.
                // SizeMetric reads codeFilePath → returns the actual LOC of the refactored file.
                JavaClassSnapshot cxSnap = new JavaClassSnapshot(desc.gitPath(), GIT_REF, cxFile);
                Map<String, String> cxComputed = computer.compute(new ClassMetricInput(cxSnap, GIT_REF, releaseDate));

                String cxKey = desc.simpleName() + "/" + variant + "/" + fileName;
                int cxNSmells = cxSmells.getOrDefault(cxKey, 0);
                rows.add(buildRow(desc.simpleName(), variant, cxComputed, cxNSmells));
            }
        }

        return rows;
    }

    private Map<String, String> buildRow(String className, String version,
                                         Map<String, String> computed, int nSmells) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("class_name", className);
        row.put("version", version);
        row.put("LOC",              computed.getOrDefault("LOC",              "0"));
        row.put("LOC_Touched",      computed.getOrDefault("LOC_Touched",      "0"));
        row.put("NR",               computed.getOrDefault("NR",               "0"));
        row.put("Nfix",             "0"); // not computable without Jira data in M4
        row.put("Nauth",            computed.getOrDefault("Nauth",            "0"));
        row.put("LOC_Added",        computed.getOrDefault("LOC_Added",        "0"));
        row.put("Max_LOC_Added",    computed.getOrDefault("Max_LOC_Added",    "0"));
        row.put("Avg_LOC_Added",    computed.getOrDefault("Avg_LOC_Added",    "0"));
        row.put("Churn",            computed.getOrDefault("Churn",            "0"));
        row.put("Max_Churn",        computed.getOrDefault("Max_Churn",        "0"));
        row.put("Avg_Churn",        computed.getOrDefault("Avg_Churn",        "0"));
        row.put("Change_Set_Size",  computed.getOrDefault("Change_Set_Size",  "0"));
        row.put("Max_Change_Set",   computed.getOrDefault("Max_Change_Set",   "0"));
        row.put("Avg_Change_Set",   computed.getOrDefault("Avg_Change_Set",   "0"));
        row.put("Age",              computed.getOrDefault("Age",              "0"));
        row.put("Weighted_Age",     computed.getOrDefault("Weighted_Age",     "0"));
        row.put("EXP",              computed.getOrDefault("EXP",              "0"));
        row.put("SEXP",             computed.getOrDefault("SEXP",             "0"));
        row.put("Previous_Release_Code_Smells", String.valueOf(nSmells));
        return row;
    }

    @SuppressWarnings("java:S4036")
    private void extractFromGit(String gitPath, Path dest) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "show", GIT_REF + ":" + gitPath);
            pb.directory(repoDir);
            Process process = pb.start();
            byte[] content = process.getInputStream().readAllBytes();
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("git show failed for " + gitPath + " (exit " + exit + ")");
            }
            Files.createDirectories(dest.getParent());
            Files.write(dest, content);
            log.info("Extracted {} ({} bytes)", dest.getFileName(), content.length);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("Interrupted running git show", e));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("java:S4036")
    private LocalDate resolveReleaseDate() {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "log", "-1", "--format=%ci", GIT_REF);
            pb.directory(repoDir);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor();
            if (output.length() >= 10) {
                return LocalDate.parse(output.substring(0, 10));
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while resolving release date for {}", GIT_REF);
        } catch (Exception e) {
            log.warn("Could not resolve release date for {}: {}", GIT_REF, e.getMessage());
        }
        return LocalDate.now(ZoneOffset.UTC);
    }

    private void deleteTempDir(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException _) { /* best-effort */ }
            });
        } catch (IOException e) {
            log.warn("Could not delete temp dir {}: {}", dir, e.getMessage());
        }
    }
}
