package com.isw2project.buggyness;

import com.isw2project.gitextractor.GitCommandException;
import com.isw2project.model.Issue;
import com.isw2project.model.ReleaseSnapshot;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates the bugginess labeling workflow.
 * Delegates all logic to CommitIndexService and BugginessLabelerService.
 */
public class BugginessOrchestrator {

    private final CommitIndexService commitIndexService;
    private final BugginessLabelerService labelerService;

    public BugginessOrchestrator(CommitIndexService commitIndexService,
                                 BugginessLabelerService labelerService) {
        this.commitIndexService = commitIndexService;
        this.labelerService = labelerService;
    }

    /**
     * Builds the commit index and labels each snapshot as buggy or not.
     * Snapshots are mutated in place — isBuggy is set directly on each JavaClassSnapshot.
     *
     * @param snapshots release snapshots produced by the Git extraction phase
     * @param issues    all Jira issues from the cleaned project data
     */
    public void labelSnapshots(List<ReleaseSnapshot> snapshots, List<Issue> issues)
            throws GitCommandException {
        Map<String, Set<String>> issueToFilesIndex = commitIndexService.buildIssueToFilesIndex();
        labelerService.label(snapshots, issues, issueToFilesIndex);
    }
}