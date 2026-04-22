package com.isw2project.model;

import java.util.List;

/**
 * Aggregates all Java class snapshots extracted from a single release tag.
 */
public class ReleaseSnapshot {

    private final String release;
    private final List<JavaClassSnapshot> classes;

    public ReleaseSnapshot(String release, List<JavaClassSnapshot> classes) {
        this.release = release;
        this.classes = classes;
    }

    public String getRelease() {
        return release;
    }

    public List<JavaClassSnapshot> getClasses() {
        return classes;
    }
}