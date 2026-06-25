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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

public class ClassSelectionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ClassSelectionOrchestrator.class);

    private final ReleaseSourceService releaseSourceService;
    private final NsmellsService nsmellsService;
    private final ClassSizeFilterService sizeFilterService;
    private final ClassSelectorService selectorService;

    public ClassSelectionOrchestrator(ReleaseSourceService releaseSourceService,
                                       NsmellsService nsmellsService,
                                       ClassSizeFilterService sizeFilterService,
                                       ClassSelectorService selectorService) {
        this.releaseSourceService = releaseSourceService;
        this.nsmellsService = nsmellsService;
        this.sizeFilterService = sizeFilterService;
        this.selectorService = selectorService;
    }

    public void run(RefactoringConfig config, int batchSize, double cpuFraction) {
        Path sourceBaseDir = Path.of(config.getExtractedSourceDir());
        releaseSourceService.ensureExtracted(config.getLastRelease(), sourceBaseDir);

        Path sourceDir = sourceBaseDir.resolve(config.getLastRelease());
        log.info("Analyzing release '{}' from {}", config.getLastRelease(), sourceDir);

        LinkedHashMap<String, Integer> allSmells = nsmellsService.computeSmells(sourceDir, batchSize, cpuFraction);
        log.info("Computed Nsmells for {} classes", allSmells.size());

        LinkedHashMap<String, Integer> filtered = sizeFilterService.filter(
                allSmells, sourceDir, config.getSmallClassLocThreshold());

        int x = config.getSelectionX();
        List<String> selected = selectorService.select(filtered, x);

        List<String> ranked = new ArrayList<>(filtered.keySet());
        log.info("=== CLASS SELECTION RESULT (G=7, X = 7 mod 5 = {}, case {}: first+{} and last-{}) ===",
                x, x, x, x);
        log.info("Rank {}: {} [Nsmells={}]", x + 1, selected.get(0), filtered.get(selected.get(0)));
        log.info("Rank {}: {} [Nsmells={}]", ranked.size() - x, selected.get(1), filtered.get(selected.get(1)));

        writeCsv(config, sourceDir, filtered, selected);
    }

    private void writeCsv(RefactoringConfig config, Path sourceDir,
                           LinkedHashMap<String, Integer> filtered, List<String> selected) {
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

    private long locOf(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.count();
        } catch (IOException e) {
            return 0;
        }
    }
}
