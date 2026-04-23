package com.isw2project.model;

import java.nio.file.Path;

/**
 * Represents the state of a production Java source file at a specific release.
 * The source code is not stored in memory but written to disk; getCode() returns its path.
 */
public class JavaClassSnapshot {

    private final String classPath;
    private final String release;
    private final Path codeFilePath;
    private boolean buggy;

    public JavaClassSnapshot(String classPath, String release, Path codeFilePath) {
        this.classPath = classPath;
        this.release = release;
        this.codeFilePath = codeFilePath;
        this.buggy = false;
    }

    public String getClassPath() { return classPath; }
    public String getRelease()   { return release; }

    /** Returns the path on disk where the source code for this snapshot is stored. */
    public Path getCode()        { return codeFilePath; }

    public boolean isBuggy()             { return buggy; }
    public void setBuggy(boolean buggy)  { this.buggy = buggy; }

    @Override
    public String toString() {
        return "JavaClassSnapshot{classPath='" + classPath + "', release='" + release + "', buggy=" + buggy + "}";
    }
}