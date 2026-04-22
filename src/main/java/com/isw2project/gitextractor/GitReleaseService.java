package com.isw2project.gitextractor;

import com.isw2project.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves Jira versions to Git tags and returns the first 33% of matched releases.
 *
 * Versions are sorted by releaseDate before the 33% cut is applied.
 * The returned map preserves insertion order and maps each Jira version name to its Git tag,
 * so that callers can use the Jira name as the release identifier while using the tag for checkout.
 *
 * Resolution strategy per version:
 *   1. Exact tag match (e.g. "1.0.0" -> "1.0.0")
 *   2. Suffix tag match, excluding SNAPSHOTs (e.g. "0.9.6" -> "0.9.6-incubating")
 *   3. No match: version is skipped with a warning.
 *
 * When compensateSkips is true, skipped versions are compensated by extending the window
 * beyond the strict 33% boundary.
 */
public class GitReleaseService {

    private static final Logger log = LoggerFactory.getLogger(GitReleaseService.class);

    private final File repoDir;
    private final boolean compensateSkips;

    public GitReleaseService(File repoDir, boolean compensateSkips) {
        this.repoDir = repoDir;
        this.compensateSkips = compensateSkips;
    }

    /**
     * Returns a LinkedHashMap of (jiraVersionName -> gitTag) for the first 33% of releases,
     * sorted chronologically by releaseDate.
     */
    public Map<String, String> resolveFirstThirdTags(List<Version> versions) throws GitCommandException {
        List<Version> sorted = versions.stream()
                .sorted(Comparator.comparing(Version::getReleaseDate))
                .toList();

        Set<String> availableTags = fetchAllTags();
        int targetCount = Math.max(1, (int) Math.ceil(sorted.size() / 3.0));
        log.info("Total versions: {}, target count (33%): {}, compensateSkips: {}",
                sorted.size(), targetCount, compensateSkips);

        Map<String, String> resolved = new LinkedHashMap<>();
        int skipped = 0;

        for (Version version : sorted) {
            String tag = resolveTag(version.getName(), availableTags);

            if (tag == null) {
                log.warn("No Git tag found for Jira version '{}', skipping.", version.getName());
                skipped++;
            } else {
                resolved.put(version.getName(), tag);
            }

            int limit = compensateSkips ? targetCount + skipped : targetCount;
            if (resolved.size() == limit) {
                break;
            }
        }

        return resolved;
    }

    private String resolveTag(String versionName, Set<String> availableTags) {
        if (availableTags.contains(versionName)) {
            return versionName;
        }
        for (String tag : availableTags) {
            if (tag.startsWith(versionName + "-") && !tag.contains("SNAPSHOT")) {
                return tag;
            }
        }
        return null;
    }

    private Set<String> fetchAllTags() throws GitCommandException {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "tag");
            pb.directory(repoDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            Set<String> tags = new HashSet<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        tags.add(trimmed);
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new GitCommandException("git tag failed with exit code: " + exitCode);
            }

            return tags;

        } catch (IOException e) {
            throw new GitCommandException("Failed to start git tag process", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitCommandException("git tag process was interrupted", e);
        }
    }
}