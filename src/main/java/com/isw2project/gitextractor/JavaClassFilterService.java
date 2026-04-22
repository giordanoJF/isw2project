package com.isw2project.gitextractor;

/**
 * Determines whether a repository-relative file path is a production Java source file.
 * Test classes are identified by directory name and file name conventions.
 */
public class JavaClassFilterService {

    /**
     * Returns true only if the path ends with ".java" and does not belong to a test directory
     * or follow a test class naming convention.
     */
    public boolean isProductionJavaFile(String repoRelativePath) {
        String normalized = repoRelativePath.replace("\\", "/");
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);

        return normalized.endsWith(".java")
                && !normalized.contains("/test/")
                && !normalized.contains("/tests/")
                && !fileName.endsWith("Test.java")
                && !fileName.endsWith("Tests.java");
    }
}