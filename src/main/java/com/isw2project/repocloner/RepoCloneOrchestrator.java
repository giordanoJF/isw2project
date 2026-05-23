package com.isw2project.repocloner;

import com.isw2project.config.GitConfig;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Ensures that the project repository is available locally before Milestone 1 runs.
 *
 * Checks whether the target directory already contains a Git repository (.git folder).
 * If it does, the clone is skipped - subsequent runs do not re-download the repo.
 * If it does not, delegates the actual clone to RepoCloneService.
 *
 * The clone URL and target directory are read from GitConfig (config.yaml), keeping
 * this class free of hardcoded paths or URLs.
 */
public class RepoCloneOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RepoCloneOrchestrator.class);

    private final RepoCloneService cloneService;

    public RepoCloneOrchestrator(RepoCloneService cloneService) {
        this.cloneService = cloneService;
    }

    public void ensureCloned(GitConfig config) throws GitAPIException {
        File repoDir = new File(config.getRepoDir());
        File gitDir = new File(repoDir, ".git");
        if (gitDir.exists()) {
            log.info("Repository already present at '{}'. Skipping clone.", repoDir.getPath());
            return;
        }
        cloneService.clone(config.getCloneUrl(), repoDir);
    }
}
