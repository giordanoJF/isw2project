package com.isw2project.model;

import java.nio.file.Path;

/**
 * Represents the state of a production Java source file at a specific release tag.
 * The source code is not stored in memory but written to disk; getCode() returns its path.
 */
public class JavaClassSnapshot {

    private final String classPath;
    private final String release;
    private final Path codeFilePath;

    public JavaClassSnapshot(String classPath, String release, Path codeFilePath) {
        this.classPath = classPath;
        this.release = release;
        this.codeFilePath = codeFilePath;
    }

    public String getClassPath() {
        return classPath;
    }

    public String getRelease() {
        return release;
    }

    /**
     * Returns the path on disk where the source code for this snapshot is stored.
     */
    public Path getCode() {
        return codeFilePath;
    }

    @Override
    public String toString() {
        return "JavaClassSnapshot{classPath='" + classPath + "', release='" + release + "'}";
    }
}