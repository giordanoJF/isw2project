package com.isw2project.gitextractor;

import com.isw2project.model.ReleaseSnapshot;
import com.isw2project.model.Version;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the Git extraction workflow.
 * Coordinates GitReleaseService and GitFileExtractorService without containing business logic.
 */
public class GitExtractorOrchestrator {

    private final GitReleaseService releaseService;
    private final GitFileExtractorService fileExtractorService;

    /**
     * @param repoDir         path to the locally cloned Git repository
     * @param outputDir       directory where extracted source files will be stored
     * @param compensateSkips when true, versions skipped due to missing tags are compensated
     *                        by extending the window beyond the strict 33% boundary
     */
    public GitExtractorOrchestrator(File repoDir, File outputDir, boolean compensateSkips) {
        JavaClassFilterService classFilter = new JavaClassFilterService();
        this.releaseService = new GitReleaseService(repoDir, compensateSkips);
        this.fileExtractorService = new GitFileExtractorService(repoDir, outputDir, classFilter);
    }

    /**
     * Extracts all production Java class snapshots for the first 33% of releases.
     * Each ReleaseSnapshot is identified by the Jira version name, not the Git tag.
     *
     * @param versions Jira versions, unordered — sorting is applied internally
     */
    public List<ReleaseSnapshot> extractSnapshots(List<Version> versions) throws GitCommandException {
        Map<String, String> jiraNameToGitTag = releaseService.resolveFirstThirdTags(versions);
        List<ReleaseSnapshot> result = new ArrayList<>();

        for (Map.Entry<String, String> entry : jiraNameToGitTag.entrySet()) {
            String jiraName = entry.getKey();
            String gitTag = entry.getValue();
            result.add(new ReleaseSnapshot(jiraName, fileExtractorService.extractClassesForRelease(jiraName, gitTag)));
        }

        return result;
    }
}