package com.isw2project.repocloner;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Performs a full clone of a remote Git repository using JGit.
 *
 * WHY JGIT INSTEAD OF THE GIT CLI:
 * Invoking `git clone` via ProcessBuilder would require the git command-line tool
 * to be installed on the machine running this project. JGit is a pure Java
 * implementation of the Git protocol (no native binary required), so the clone works
 * on any machine that has a JVM, regardless of whether git is installed.
 *
 * WHY FULL CLONE (no depth limit):
 * CommitIndexService and GitLogStatsService scan the entire commit history to build
 * bug-fix and file-change indexes. A shallow clone exposes only a handful of commits,
 * causing those services to label nearly all files as buggy (observed: ~60% instead
 * of the expected ~9% with a complete history). The download is larger but required.
 *
 * No authentication is required: the target repository is public and is accessed
 * over plain HTTPS.
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
