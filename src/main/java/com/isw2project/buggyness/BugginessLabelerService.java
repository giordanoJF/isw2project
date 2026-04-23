package com.isw2project.buggyness;

import com.isw2project.model.Issue;
import com.isw2project.model.JavaClassSnapshot;
import com.isw2project.model.ReleaseSnapshot;
import com.isw2project.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Labels each JavaClassSnapshot as buggy or not.
 *
 * A snapshot (classPath, release) is buggy if there exists at least one issue such that:
 *   1. The issue has the snapshot's release among its affected versions.
 *   2. At least one commit referencing the issue key touches the snapshot's classPath.
 */
public class BugginessLabelerService {

    private static final Logger log = LoggerFactory.getLogger(BugginessLabelerService.class);

    /**
     * Iterates over all snapshots and sets isBuggy where the conditions are met.
     *
     * @param snapshots         release snapshots to label
     * @param issues            all Jira issues
     * @param issueToFilesIndex map of issueKey -> files touched by its commits
     */
    public void label(List<ReleaseSnapshot> snapshots, List<Issue> issues,
                      Map<String, Set<String>> issueToFilesIndex) {

        int totalSnapshots = snapshots.stream().mapToInt(r -> r.getClasses().size()).sum();
        int[] labeledBuggy = {0};
        int issuesWithNoCommits = 0;

        for (Issue issue : issues) {
            Set<String> touchedFiles = resolveTouchedFiles(issue, issueToFilesIndex);
            if (touchedFiles.isEmpty()) {
                issuesWithNoCommits++;
            } else {
                Set<String> affectedVersionNames = collectVersionNames(issue.getFields().getAffectedVersions());
                labeledBuggy[0] += labelMatchingSnapshots(snapshots, affectedVersionNames, touchedFiles);
            }
        }

        log.info("Bugginess labeling complete: {}/{} snapshots labeled buggy.", labeledBuggy[0], totalSnapshots);
        log.info("Issues with no matching commits in index: {}.", issuesWithNoCommits);
    }

    private Set<String> resolveTouchedFiles(Issue issue, Map<String, Set<String>> issueToFilesIndex) {
        List<Version> affectedVersions = issue.getFields().getAffectedVersions();
        if (affectedVersions == null || affectedVersions.isEmpty()) {
            return Set.of();
        }
        Set<String> touchedFiles = issueToFilesIndex.get(issue.getKey());
        return touchedFiles != null ? touchedFiles : Set.of();
    }

    private int labelMatchingSnapshots(List<ReleaseSnapshot> snapshots,
                                       Set<String> affectedVersionNames,
                                       Set<String> touchedFiles) {
        int count = 0;
        for (ReleaseSnapshot release : snapshots) {
            if (affectedVersionNames.contains(release.getRelease())) {
                count += labelClassesInRelease(release, touchedFiles);
            }
        }
        return count;
    }

    private int labelClassesInRelease(ReleaseSnapshot release, Set<String> touchedFiles) {
        int count = 0;
        for (JavaClassSnapshot snapshot : release.getClasses()) {
            if (!snapshot.isBuggy() && touchedFiles.contains(snapshot.getClassPath())) {
                snapshot.setBuggy(true);
                count++;
            }
        }
        return count;
    }

    private Set<String> collectVersionNames(List<Version> versions) {
        return versions.stream()
                .map(Version::getName)
                .collect(Collectors.toCollection(HashSet::new));
    }
}