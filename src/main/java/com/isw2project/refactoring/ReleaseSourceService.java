package com.isw2project.refactoring;

import com.isw2project.gitextractor.GitCommandException;
import com.isw2project.gitextractor.GitFileExtractorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class ReleaseSourceService {

    private static final Logger log = LoggerFactory.getLogger(ReleaseSourceService.class);

    private final GitFileExtractorService extractor;

    public ReleaseSourceService(GitFileExtractorService extractor) {
        this.extractor = extractor;
    }

    public void ensureExtracted(String releaseName, Path targetBaseDir) {
        if (Files.exists(targetBaseDir.resolve(releaseName))) {
            log.info("Source for release '{}' already present at {}", releaseName, targetBaseDir);
            return;
        }
        log.info("Extracting release '{}' from git to {}", releaseName, targetBaseDir);
        try {
            extractor.extractClassesForRelease(releaseName, releaseName);
            log.info("Extraction complete for release '{}'", releaseName);
        } catch (GitCommandException e) {
            log.error("Failed to extract release '{}': {}", releaseName, e.getMessage(), e);
        }
    }
}
