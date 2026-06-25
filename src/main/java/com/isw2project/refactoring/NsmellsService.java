package com.isw2project.refactoring;

import com.isw2project.gitextractor.JavaClassFilterService;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NsmellsService {

    private static final Logger log = LoggerFactory.getLogger(NsmellsService.class);

    private static final String[] RULESETS = {
            "category/java/bestpractices.xml",
            "category/java/design.xml",
            "category/java/errorprone.xml",
            "category/java/codestyle.xml"
    };

    private final JavaClassFilterService filterService;

    public NsmellsService(JavaClassFilterService filterService) {
        this.filterService = filterService;
    }

    public Map<String, Integer> computeSmells(Path sourceDir, int batchSize, double cpuFraction) {
        List<Path> files = collectProductionFiles(sourceDir);
        log.info("Found {} production Java files in {}", files.size(), sourceDir);

        Map<String, String> absToRel = new HashMap<>();
        for (Path file : files) {
            String rel = sourceDir.relativize(file).toString().replace("\\", "/");
            absToRel.put(file.toAbsolutePath().toString(), rel);
        }

        Map<String, Integer> smells = new HashMap<>();
        absToRel.values().forEach(rel -> smells.put(rel, 0));

        for (int i = 0; i < files.size(); i += batchSize) {
            List<Path> batch = new ArrayList<>(files.subList(i, Math.min(i + batchSize, files.size())));
            analyzeSubBatch(batch, smells, absToRel, cpuFraction);
            log.info("PMD progress: {}/{} files analyzed", Math.min(i + batchSize, files.size()), files.size());
        }

        return smells.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private List<Path> collectProductionFiles(Path sourceDir) {
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> filterService.isProductionJavaFile(
                            sourceDir.relativize(p).toString().replace("\\", "/")))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void analyzeSubBatch(List<Path> batch, Map<String, Integer> smells,
                                  Map<String, String> absToRel, double cpuFraction) {
        PMDConfiguration config = new PMDConfiguration();
        config.setDefaultLanguageVersion(
                net.sourceforge.pmd.lang.LanguageRegistry.PMD
                        .getLanguageById("java")
                        .getDefaultVersion()
        );
        for (String ruleset : RULESETS) config.addRuleSet(ruleset);
        batch.forEach(config::addInputPath);
        config.setReportFormat("empty");
        config.setAnalysisCacheLocation(null);
        int threads = Math.max(1, (int) (Runtime.getRuntime().availableProcessors() * cpuFraction));
        config.setThreads(threads);

        try (PmdAnalysis pmd = PmdAnalysis.create(config)) {
            Report report = pmd.performAnalysisAndCollectReport();
            for (RuleViolation v : report.getViolations()) {
                String rel = absToRel.get(v.getFileId().getAbsolutePath());
                if (rel != null) smells.merge(rel, 1, Integer::sum);
            }
        } catch (Exception e) {
            log.warn("PMD batch analysis failed: {}", e.getMessage());
        }
    }
}
