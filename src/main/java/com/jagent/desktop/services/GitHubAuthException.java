package com.jagent.desktop.services;

import java.io.IOException;
import java.time.Instant;
import org.jetbrains.annotations.Nullable;

/** Indicates that a GitHub credential must be replaced or renewed. */
public final class GitHubAuthException extends IOException {
    private final String connectionName;
    private final @Nullable Instant expiresAt;

    public GitHubAuthException(
            final String connectionName, @Nullable final Instant expiresAt, final Throwable cause) {
        super(message(connectionName, expiresAt), cause);
        this.connectionName = connectionName;
        this.expiresAt = expiresAt;
    }

    public GitHubAuthException(final String connectionName, @Nullable final Instant expiresAt) {
        this(connectionName, expiresAt, null);
    }

    public String connectionName() {
        return connectionName;
    }

    public @Nullable Instant expiresAt() {
        return expiresAt;
    }

    private static String message(final String connectionName, @Nullable final Instant expiresAt) {
        final String status =
                expiresAt == null ? "could not be validated" : "is expired or expiring soon";
        return "GitHub connection '"
                + connectionName
                + "' "
                + status
                + ". Replace the personal access token in Project Settings or reauthenticate the GitHub CLI account.";
    }
}
