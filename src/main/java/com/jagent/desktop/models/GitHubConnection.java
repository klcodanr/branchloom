package com.jagent.desktop.models;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Reusable GitHub authentication metadata. The secret is kept in the OS keyring. */
public record GitHubConnection(
        String id,
        String name,
        String host,
        @Nullable String user,
        boolean usePersonalAccessToken,
        @Nullable String credentialKey) {
    public GitHubConnection {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A GitHub connection name is required.");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("A GitHub connection host is required.");
        }
        if (usePersonalAccessToken && (credentialKey == null || credentialKey.isBlank())) {
            throw new IllegalArgumentException("A PAT connection requires a credential key.");
        }
    }

    public static GitHubConnection cli(final String name, final String host, final String user) {
        return new GitHubConnection(UUID.randomUUID().toString(), name, host, user, false, null);
    }

    public static GitHubConnection personalAccessToken(
            final String name, final String host, final String credentialKey) {
        return new GitHubConnection(
                UUID.randomUUID().toString(), name, host, null, true, credentialKey);
    }
}
