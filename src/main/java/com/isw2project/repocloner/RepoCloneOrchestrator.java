package com.isw2project.repocloner;

import com.isw2project.config.GitConfig;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Ensures that the project repository is available locally before Milestone 1 runs.
 * Skips the clone if a .git folder already exists in the target directory.
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
