package com.isw2project.refactoring;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SmellDetailService {

    private static final Logger log = LoggerFactory.getLogger(SmellDetailService.class);

    private static final String[] RULESETS = {
            "category/java/bestpractices.xml",
            "category/java/design.xml",
            "category/java/errorprone.xml",
            "category/java/codestyle.xml"
    };

    public Map<String, List<ViolationDetail>> computeDetails(
            List<String> relativePaths, Path sourceDir, double cpuFraction) {

        Map<String, List<ViolationDetail>> result = new LinkedHashMap<>();
        for (String rel : relativePaths) {
            result.put(rel, new ArrayList<>());
        }

        List<Path> absPaths = relativePaths.stream().map(sourceDir::resolve).toList();

        PMDConfiguration config = new PMDConfiguration();
        config.setDefaultLanguageVersion(
                net.sourceforge.pmd.lang.LanguageRegistry.PMD
                        .getLanguageById("java")
                        .getDefaultVersion()
        );
        for (String ruleset : RULESETS) config.addRuleSet(ruleset);
        absPaths.forEach(config::addInputPath);
        config.setReportFormat("empty");
        config.setAnalysisCacheLocation(null);
        int threads = Math.max(1, (int) (Runtime.getRuntime().availableProcessors() * cpuFraction));
        config.setThreads(threads);

        try (PmdAnalysis pmd = PmdAnalysis.create(config)) {
            Report report = pmd.performAnalysisAndCollectReport();
            for (RuleViolation v : report.getViolations()) {
                String absPath = v.getFileId().getAbsolutePath();
                for (String rel : relativePaths) {
                    if (sourceDir.resolve(rel).toAbsolutePath().toString().equals(absPath)) {
                        result.get(rel).add(new ViolationDetail(
                                v.getRule().getName(),
                                v.getRule().getRuleSetName(),
                                v.getBeginLine(),
                                v.getEndLine(),
                                v.getDescription()
                        ));
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("PMD detail analysis failed: {}", e.getMessage());
        }

        for (String rel : relativePaths) {
            result.get(rel).sort((a, b) -> Integer.compare(a.beginLine(), b.beginLine()));
        }

        log.info("Smell detail computed: {} violations in class A, {} in class B",
                result.get(relativePaths.get(0)).size(),
                result.get(relativePaths.get(1)).size());

        return result;
    }
}
