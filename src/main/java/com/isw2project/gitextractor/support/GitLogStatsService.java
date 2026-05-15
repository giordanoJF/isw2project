package com.isw2project.gitextractor.support;
import com.isw2project.gitextractor.GitCommandException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a global in-memory index of all commit stats from the entire git history in a single
 * git log invocation. All metrics read from this index instead of spawning separate git processes.
 *
 * Index structure: filePath -> list of CommitStat (chronological, oldest first).
 * Each CommitStat carries author, date, lines added/deleted, and change set size for one commit.
 */
public class GitLogStatsService {

    private static final Logger log = LoggerFactory.getLogger(GitLogStatsService.class);
    private static final Pattern NUMSTAT_PATTERN = Pattern.compile("(\\d+)\\s+(\\d+)\\s+(.+)");
    private static final Pattern COMMIT_PATTERN = Pattern.compile("COMMIT:([^|]+)\\|([^|]+)");

    private final File repoDir;
    private Map<String, List<CommitStat>> index = null;
    // Cache for ref -> date resolutions: there are only as many entries as releases (e.g. 12),
    // so caching avoids spawning one git process per snapshot per metric call (e.g. 15000+).
    private final Map<String, LocalDate> refDateCache = new HashMap<>();
    //Cache FOR (filePath, gitRef) -> filtered stats: avoids re-filtering the full commit list
    // for every metric call on the same snapshot. Key format: "filePath::gitRef".
    private final Map<String, List<CommitStat>> filteredStatsCache = new HashMap<>();

    public GitLogStatsService(File repoDir) {
        this.repoDir = repoDir;
        // Build the index eagerly at construction time so the cost is not attributed
        // to the first metric that calls getCommitStats() (which would skew timing).
        ensureIndexBuilt();
    }

    /**
     * Returns all commit stats for the given file up to and including the given git ref.
     * The index is built once on first call and reused for all subsequent calls.
     */
    public List<CommitStat> getCommitStats(String filePath, String gitRef) {
        String cacheKey = filePath + "::" + gitRef;
        if (filteredStatsCache.containsKey(cacheKey)) {
            return filteredStatsCache.get(cacheKey);
        }
        ensureIndexBuilt();
        List<CommitStat> all = index.getOrDefault(filePath, Collections.emptyList());
        LocalDate refDate = resolveRefDate(gitRef);
        List<CommitStat> filtered = refDate == null ? all :
                all.stream().filter(s -> !s.getDate().isAfter(refDate)).toList();
        filteredStatsCache.put(cacheKey, filtered);
        return filtered;
    }

    /** Returns the full file→commits index. Used by metrics that need to query global author history. */
    public Map<String, List<CommitStat>> getFullIndex() {
        ensureIndexBuilt();
        return Collections.unmodifiableMap(index);
    }

    /**
     * Returns the date of the first commit that added the given file, or null if not found.
     */
    public LocalDate getFirstCommitDate(String filePath, String gitRef) {
        List<CommitStat> stats = getCommitStats(filePath, gitRef);
        if (stats.isEmpty()) return null;
        return stats.get(0).getDate();
    }

    private void ensureIndexBuilt() {
        if (index != null) return;
        log.info("Building git log index...");  //tmp
        try {
            index = buildIndex();
            log.info("Git log index built: {} unique files tracked.", index.size());
        } catch (GitCommandException e) {
            log.error("Failed to build git log index: {}", e.getMessage(), e);
            index = new HashMap<>();
        }
    }

    private Map<String, List<CommitStat>> buildIndex() throws GitCommandException {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "git", "log", "--all", "--numstat", "--format=COMMIT:%ae|%ci", "--reverse"
            );
            pb.directory(repoDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            Map<String, List<CommitStat>> result = new HashMap<>();

            String currentAuthor = null;
            LocalDate currentDate = null;
            List<String> currentFiles = new ArrayList<>();
            Map<String, int[]> currentFileStats = new HashMap<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("COMMIT:")) {
                        flushCommit(result, currentAuthor, currentDate, currentFiles, currentFileStats);
                        Matcher m = COMMIT_PATTERN.matcher(line);
                        if (m.find()) {
                            currentAuthor = m.group(1).trim();
                            currentDate = LocalDate.parse(m.group(2).trim().substring(0, 10));
                        }
                        currentFiles = new ArrayList<>();
                        currentFileStats = new HashMap<>();
                    } else if (!line.isBlank()) {
                        Matcher m = NUMSTAT_PATTERN.matcher(line.trim());
                        if (m.matches()) {
                            String filePath = m.group(3).trim();
                            int added = Integer.parseInt(m.group(1));
                            int deleted = Integer.parseInt(m.group(2));
                            currentFiles.add(filePath);
                            currentFileStats.put(filePath, new int[]{added, deleted});
                        }
                    }
                }
                flushCommit(result, currentAuthor, currentDate, currentFiles, currentFileStats);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new GitCommandException("git log failed with exit code: " + exitCode);
            }

            return result;

        } catch (IOException e) {
            throw new GitCommandException("Failed to start git log process", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitCommandException("git log process was interrupted", e);
        }
    }

    private void flushCommit(Map<String, List<CommitStat>> result,
                             String author, LocalDate date,
                             List<String> files, Map<String, int[]> fileStats) {
        if (author == null) return;
        int changeSetSize = files.size();
        for (String filePath : files) {
            int[] stats = fileStats.get(filePath);
            result.computeIfAbsent(filePath, k -> new ArrayList<>())
                    .add(new CommitStat(author, date, stats[0], stats[1], changeSetSize));
        }
    }

    private LocalDate resolveRefDate(String gitRef) {
        // Return cached result if available: each gitRef corresponds to one release tag,
        // so this cache is hit thousands of times per tag instead of spawning a new process each time.
        if (refDateCache.containsKey(gitRef)) {
            return refDateCache.get(gitRef);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("git", "log", "-1", "--format=%ci", gitRef);
            pb.directory(repoDir);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                line = reader.readLine();
            }
            process.waitFor();
            if (line != null && !line.isBlank()) {
                LocalDate date = LocalDate.parse(line.trim().substring(0, 10));
                refDateCache.put(gitRef, date);
                return date;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while resolving date for ref '{}': {}", gitRef, e.getMessage());
        } catch (Exception e) {
            log.warn("Could not resolve date for ref '{}': {}", gitRef, e.getMessage());
        }

        // Cache null result too to avoid retrying a ref that will never resolve.
        refDateCache.put(gitRef, null);
        return null;
    }

    // -------------------------------------------------------------------------

    public static class CommitStat {
        private final String authorEmail;
        private final LocalDate date;
        private final int linesAdded;
        private final int linesDeleted;
        private final int changeSetSize;

        public CommitStat(String authorEmail, LocalDate date,
                          int linesAdded, int linesDeleted, int changeSetSize) {
            this.authorEmail = authorEmail;
            this.date = date;
            this.linesAdded = linesAdded;
            this.linesDeleted = linesDeleted;
            this.changeSetSize = changeSetSize;
        }

        public String getAuthorEmail() { return authorEmail; }
        public LocalDate getDate()     { return date; }
        public int getLinesAdded()     { return linesAdded; }
        public int getLinesDeleted()   { return linesDeleted; }
        public int getChangeSetSize()  { return changeSetSize; }
        public int getChurn()          { return linesAdded + linesDeleted; }
        public int getLOCTouched()     { return linesAdded + linesDeleted; }
    }
}