package com.isw2project.gitextractor;

/**
 * Thrown when a Git command fails or is interrupted during execution.
 */
public class GitCommandException extends Exception {

    public GitCommandException(String message) {
        super(message);
    }

    public GitCommandException(String message, Throwable cause) {
        super(message, cause);
    }
}