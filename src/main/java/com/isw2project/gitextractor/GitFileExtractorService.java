package com.isw2project.gitextractor;

import com.isw2project.model.JavaClassSnapshot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts production Java source files from a Git tag and persists them to an output directory.
 * The output folder name uses the Jira version name, not the Git tag.
 * Output structure: outputDir/jiraVersionName/original/repo/relative/path/File.java
 */
public class GitFileExtractorService {

    private final File repoDir;
    private final File outputDir;
    private final JavaClassFilterService classFilter;

    public GitFileExtractorService(File repoDir, File outputDir, JavaClassFilterService classFilter) {
        this.repoDir = repoDir;
        this.outputDir = outputDir;
        this.classFilter = classFilter;
    }

    /**
     * Lists all production Java files at the given Git tag, extracts their source code,
     * writes each file to disk under the Jira version name folder, and returns the snapshots.
     *
     * @param jiraVersionName the Jira version name used as folder name and snapshot identifier
     * @param gitTag          the Git tag used for checkout
     */
    public List<JavaClassSnapshot> extractClassesForRelease(String jiraVersionName, String gitTag)
            throws GitCommandException {
        List<String> javaFiles = listProductionJavaFiles(gitTag);
        List<JavaClassSnapshot> snapshots = new ArrayList<>();

        for (String filePath : javaFiles) {
            String content = readFileAtTag(gitTag, filePath);
            Path savedPath = saveToDisk(jiraVersionName, filePath, content);
            snapshots.add(new JavaClassSnapshot(filePath, jiraVersionName, savedPath));
        }

        return snapshots;
    }

    private List<String> listProductionJavaFiles(String gitTag) throws GitCommandException {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "ls-tree", "-r", "--name-only", gitTag);
            pb.directory(repoDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            List<String> result = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (classFilter.isProductionJavaFile(trimmed)) {
                        result.add(trimmed);
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new GitCommandException("git ls-tree failed for tag: " + gitTag + " with exit code: " + exitCode);
            }

            return result;

        } catch (IOException e) {
            throw new GitCommandException("Failed to start git ls-tree process for tag: " + gitTag, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitCommandException("git ls-tree process was interrupted for tag: " + gitTag, e);
        }
    }

    private String readFileAtTag(String gitTag, String filePath) throws GitCommandException {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "show", gitTag + ":" + filePath);
            pb.directory(repoDir);
            pb.redirectErrorStream(false);

            Process process = pb.start();
            StringBuilder content = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append(System.lineSeparator());
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new GitCommandException("git show failed for " + gitTag + ":" + filePath
                        + " with exit code: " + exitCode);
            }

            return content.toString();

        } catch (IOException e) {
            throw new GitCommandException("Failed to start git show process for " + gitTag + ":" + filePath, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitCommandException("git show process was interrupted for " + gitTag + ":" + filePath, e);
        }
    }

    private Path saveToDisk(String jiraVersionName, String filePath, String content) throws GitCommandException {
        try {
            String safeName = jiraVersionName.replaceAll("[^a-zA-Z0-9._\\-]", "_");
            File destination = new File(outputDir, safeName + File.separator + filePath.replace("/", File.separator));
            destination.getParentFile().mkdirs();

            try (FileWriter writer = new FileWriter(destination)) {
                writer.write(content);
            }

            return destination.toPath();

        } catch (IOException e) {
            throw new GitCommandException("Failed to save file to disk: " + filePath, e);
        }
    }
}