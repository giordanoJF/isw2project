package com.isw2project.repocloner;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Performs a full clone of a remote Git repository using JGit (no git CLI required).
 *
 * The clone must be full (no depth limit): CommitIndexService and GitLogStatsService
 * scan the entire history; a shallow clone produced ~60% buggy rate instead of ~9%.
 */
public class RepoCloneService {

    private static final Logger log = LoggerFactory.getLogger(RepoCloneService.class);

    public void clone(String url, File targetDir) throws GitAPIException {
        log.info("Cloning {} into {} (full clone)...", url, targetDir.getPath());
        try (var _ = Git.cloneRepository()
                .setURI(url)
                .setDirectory(targetDir)
                .setCloneAllBranches(false)
                .setProgressMonitor(new LogProgressMonitor())
                .call()) {
            log.info("Clone completed successfully.");
        }
    }

    private static class LogProgressMonitor implements ProgressMonitor {

        private static final Logger pmLog = LoggerFactory.getLogger(LogProgressMonitor.class);
        private String currentTask = "";
        private int totalWork;
        private int done;

        @Override public void start(int totalTasks) { /* total task count not used */ }

        @Override
        public void beginTask(String title, int totalWork) {
            this.currentTask = title;
            this.totalWork = totalWork;
            this.done = 0;
            if (totalWork == UNKNOWN) {
                pmLog.info("  {}...", title);
            } else {
                pmLog.info("  {}: 0/{}", title, totalWork);
            }
        }

        @Override
        public void update(int completed) {
            done += completed;
            if (totalWork != UNKNOWN && pmLog.isDebugEnabled()) {
                pmLog.debug("  {}: {}/{}", currentTask, done, totalWork);
            }
        }

        @Override
        public void endTask() {
            if (totalWork == UNKNOWN) {
                pmLog.info("  {} done.", currentTask);
            } else {
                pmLog.info("  {} done ({}/{}).", currentTask, done, totalWork);
            }
        }

        @Override public boolean isCancelled() { return false; }

        @Override public void showDuration(boolean enabled) { /* elapsed-time display not needed */ }
    }
}
