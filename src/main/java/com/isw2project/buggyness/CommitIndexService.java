package com.isw2project.buggyness;

import com.isw2project.gitextractor.GitCommandException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds an index of issueKey -> set of files touched by commits referencing that issue.
 * Parses the entire Git log history using "git log --all --name-only".
 * Issue keys are extracted from commit messages via a pattern like "OPENJPA-1234".
 */
public class CommitIndexService {

    private static final Logger log = LoggerFactory.getLogger(CommitIndexService.class);
    private static final Pattern ISSUE_KEY_PATTERN = Pattern.compile("[A-Z]+-\\d+");

    private final File repoDir;

    public CommitIndexService(File repoDir) {
        this.repoDir = repoDir;
    }

    /**
     * Returns a map of issueKey -> set of repository-relative file paths touched
     * by commits whose message contains that issue key.
     */
    public Map<String, Set<String>> buildIssueToFilesIndex() throws GitCommandException {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "git", "log", "--all", "--name-only", "--format=COMMIT:%s"
            );
            pb.directory(repoDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            Map<String, Set<String>> index = new HashMap<>();

            Set<String> currentIssueKeys = new HashSet<>();
            int totalCommits = 0;
            int matchedCommits = 0;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("COMMIT:")) {
                        totalCommits++;
                        currentIssueKeys = extractIssueKeys(line.substring(7));
                        if (!currentIssueKeys.isEmpty()) {
                            matchedCommits++;
                        }
                    } else if (!line.isBlank() && !currentIssueKeys.isEmpty()) {
                        for (String key : currentIssueKeys) {
                            index.computeIfAbsent(key, k -> new HashSet<>()).add(line.trim());
                        }
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new GitCommandException("git log failed with exit code: " + exitCode);
            }

            log.info("Commit index: {} total commits, {} with issue key, {} unique issues indexed, {} with no key.",
                    totalCommits, matchedCommits, index.size(), totalCommits - matchedCommits);

            return index;

        } catch (IOException e) {
            throw new GitCommandException("Failed to start git log process", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitCommandException("git log process was interrupted", e);
        }
    }

    private Set<String> extractIssueKeys(String commitMessage) {
        Set<String> keys = new HashSet<>();
        Matcher matcher = ISSUE_KEY_PATTERN.matcher(commitMessage);
        while (matcher.find()) {
            keys.add(matcher.group());
        }
        return keys;
    }
}