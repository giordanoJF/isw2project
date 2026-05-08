package com.isw2project.gitextractor;

import com.isw2project.model.ReleaseSnapshot;
import com.isw2project.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the Git extraction workflow.
 * Coordinates GitReleaseService and GitFileExtractorService without containing business logic.
 * Exceptions are handled internally and logged so that callers need no try-catch.
 */
public class GitExtractorOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GitExtractorOrchestrator.class);

    private final GitReleaseService releaseService;
    private final GitFileExtractorService fileExtractorService;
    private Map<String, String> lastResolvedRefs = new LinkedHashMap<>();

    public GitExtractorOrchestrator(File repoDir, File outputDir, boolean compensateSkips) {
        JavaClassFilterService classFilter = new JavaClassFilterService();
        this.releaseService = new GitReleaseService(repoDir, compensateSkips);
        this.fileExtractorService = new GitFileExtractorService(repoDir, outputDir, classFilter);
    }

    /**
     * Extracts all production Java class snapshots for the first 33% of releases.
     * Returns an empty list and logs the error if the extraction fails.
     */
    public List<ReleaseSnapshot> extractSnapshots(List<Version> versions) {
        try {
            lastResolvedRefs = releaseService.resolveFirstThirdTags(versions);
            List<ReleaseSnapshot> result = new ArrayList<>();
            for (Map.Entry<String, String> entry : lastResolvedRefs.entrySet()) {
                result.add(new ReleaseSnapshot(entry.getKey(),
                        fileExtractorService.extractClassesForRelease(entry.getKey(), entry.getValue())));
            }
            return result;
        } catch (GitCommandException e) {
            log.error("Git extraction failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Returns the map of Jira version name -> Git ref resolved during the last extractSnapshots() call.
     */
    public Map<String, String> getLastResolvedRefs() {
        return lastResolvedRefs;
    }
}