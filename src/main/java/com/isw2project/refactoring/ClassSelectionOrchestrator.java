package com.isw2project.refactoring;

import com.isw2project.config.RefactoringConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ClassSelectionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ClassSelectionOrchestrator.class);

    private final ReleaseSourceService releaseSourceService;
    private final NsmellsService nsmellsService;
    private final ClassSizeFilterService sizeFilterService;
    private final ClassSelectorService selectorService;
    private final SmellDetailService smellDetailService;

    public ClassSelectionOrchestrator(ReleaseSourceService releaseSourceService,
                                       NsmellsService nsmellsService,
                                       ClassSizeFilterService sizeFilterService,
                                       ClassSelectorService selectorService,
                                       SmellDetailService smellDetailService) {
        this.releaseSourceService = releaseSourceService;
        this.nsmellsService = nsmellsService;
        this.sizeFilterService = sizeFilterService;
        this.selectorService = selectorService;
        this.smellDetailService = smellDetailService;
    }

    public void run(RefactoringConfig config, int batchSize, double cpuFraction) {
        Path sourceBaseDir = Path.of(config.getExtractedSourceDir());
        releaseSourceService.ensureExtracted(config.getLastRelease(), sourceBaseDir);

        Path sourceDir = sourceBaseDir.resolve(config.getLastRelease());
        log.info("Analyzing release '{}' from {}", config.getLastRelease(), sourceDir);

        Map<String, Integer> allSmells = nsmellsService.computeSmells(sourceDir, batchSize, cpuFraction);
        log.info("Computed Nsmells for {} classes", allSmells.size());

        Map<String, Integer> filtered = sizeFilterService.filter(
                allSmells, sourceDir,
                config.getSmallClassLocThreshold(),
                config.getMinSmellsThreshold());

        int x = config.getSelectionX();
        List<String> selected = selectorService.select(filtered, x);

        List<String> ranked = new ArrayList<>(filtered.keySet());
        int rankA = ranked.indexOf(selected.get(0)) + 1;
        int rankB = ranked.indexOf(selected.get(1)) + 1;

        if (log.isInfoEnabled()) {
            log.info("=== CLASS SELECTION (G=7, X = 7 mod 5 = {}, case {}: first+{} and last-{}) ===",
                    x, x, x, x);
            log.info("Class A — rank {}: {} [Nsmells={}]", rankA, selected.get(0), filtered.get(selected.get(0)));
            log.info("Class B — rank {}: {} [Nsmells={}]", rankB, selected.get(1), filtered.get(selected.get(1)));
        }

        writeCsv(config, sourceDir, filtered, selected);

        Map<String, List<ViolationDetail>> details =
                smellDetailService.computeDetails(selected, sourceDir, cpuFraction);
        writeSmellReport(config, sourceDir, selected, filtered, details, ranked.size(), rankA, rankB, x);
    }

    private void writeCsv(RefactoringConfig config, Path sourceDir,
                           Map<String, Integer> filtered, List<String> selected) {
        Path outFile = Path.of(config.getOutputDir()).resolve("class_selection.csv");
        try {
            Files.createDirectories(outFile.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<String> ranked = new ArrayList<>(filtered.keySet());
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outFile, StandardCharsets.UTF_8))) {
            writer.println("rank,class_path,nsmells,loc,selected");
            for (int i = 0; i < ranked.size(); i++) {
                String path = ranked.get(i);
                int nsmells = filtered.get(path);
                long loc = locOf(sourceDir.resolve(path));
                writer.printf("%d,%s,%d,%d,%b%n", i + 1, path, nsmells, loc, selected.contains(path));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        log.info("Written {}", outFile);
    }

    private void writeSmellReport(RefactoringConfig config, Path sourceDir,
                                   List<String> selected, Map<String, Integer> filtered,
                                   Map<String, List<ViolationDetail>> details,
                                   int totalClasses, int rankA, int rankB, int x) {
        Path outFile = Path.of(config.getOutputDir()).resolve("smell_report.txt");
        String[] labels = {"A", "B"};
        int[] ranks    = {rankA, rankB};
        String[] positions = {"first+" + x, "last-" + x};

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outFile, StandardCharsets.UTF_8))) {
            w.println("=== SMELL DETAIL REPORT — Milestone 4 ===");
            w.printf("Release  : %s%n", config.getLastRelease());
            w.printf("Generated: %s%n", LocalDate.now());
            w.printf("Selection: G=7, X=7 mod 5=%d, case %d (%s and %s)%n", x, x, positions[0], positions[1]);
            w.printf("Ranked classes after filters: %d%n", totalClasses);
            w.println();

            for (int i = 0; i < selected.size(); i++) {
                String rel     = selected.get(i);
                int nsmells    = filtered.get(rel);
                long loc       = locOf(sourceDir.resolve(rel));
                List<ViolationDetail> violations = details.get(rel);

                w.println("=".repeat(80));
                w.printf("CLASS %s (rank %d, %s):%n", labels[i], ranks[i], positions[i]);
                w.printf("  Path   : %s%n", rel);
                w.printf("  LOC    : %d%n", loc);
                w.printf("  Nsmells: %d%n", nsmells);
                w.println();

                if (violations.isEmpty()) {
                    w.println("  [No violations found by PMD — class may be clean]");
                } else {
                    w.printf("  %-5s  %-45s  %-22s  %-10s  %s%n",
                            "#", "Rule", "Category", "Lines", "Message");
                    w.println("  " + "-".repeat(140));
                    for (int j = 0; j < violations.size(); j++) {
                        ViolationDetail v = violations.get(j);
                        String lineRange = v.beginLine() == v.endLine()
                                ? String.valueOf(v.beginLine())
                                : v.beginLine() + "-" + v.endLine();
                        String msg = v.description().length() > 100
                                ? v.description().substring(0, 97) + "..."
                                : v.description();
                        w.printf("  %-5d  %-45s  %-22s  %-10s  %s%n",
                                j + 1, v.ruleName(), v.ruleSetName(), lineRange, msg);
                    }
                }
                w.println();
            }

            w.println("=".repeat(80));
            w.println("COPILOT PROMPT SKELETON:");
            w.println();
            for (int i = 0; i < selected.size(); i++) {
                String rel      = selected.get(i);
                List<ViolationDetail> violations = details.get(rel);
                String simpleName = Path.of(rel).getFileName().toString().replace(".java", "");

                w.printf("--- For %s (C_0 = %s) ---%n", labels[i], simpleName);
                w.println("You are an expert Java developer. I want to improve the maintainability of");
                w.printf("the attached %s class.%n", simpleName);
                w.printf("Create C_X without changing %s functionality and by removing the%n", simpleName);
                w.println("following smells (PMD violations):");
                for (ViolationDetail v : violations) {
                    w.printf("  - [%s / %s] line %d: %s%n",
                            v.ruleSetName(), v.ruleName(), v.beginLine(), v.description());
                }
                w.println("Make sure C_X passes the existing tests.");
                w.println("Do not include in C_X changes different from what I asked.");
                w.println("C_X should replace C_0 and work with the other components of the system.");
                w.println("This is an important request; take all the time you need to provide a");
                w.println("complete and accurate answer to this request.");
                w.println();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        log.info("Written {}", outFile);
    }

    private long locOf(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.count();
        } catch (IOException _) {
            return 0;
        }
    }
}
