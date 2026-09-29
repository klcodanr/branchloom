package com.jagent.desktop.services;

import com.jagent.desktop.models.GitHubConnection;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads a PAT from the OS keyring and requires its GitHub expiration metadata. */
public final class PersonalAccessTokenProvider implements GitHubTokenProvider {
    private static final Logger LOG = LoggerFactory.getLogger(PersonalAccessTokenProvider.class);
    private final CredentialStore credentials;
    private final GitHubTokenExpiration expiration;
    private final String endpoint;

    public PersonalAccessTokenProvider(
            final CredentialStore credentials, final GitHubTokenExpiration expiration) {
        this(credentials, expiration, null);
    }

    public PersonalAccessTokenProvider(
            final CredentialStore credentials,
            final GitHubTokenExpiration expiration,
            final String endpoint) {
        this.credentials = credentials;
        this.expiration = expiration;
        this.endpoint = endpoint;
    }

    @Override
    public GitHubToken acquire(final GitHubConnection connection) throws IOException {
        final String token =
                credentials
                        .get(connection.credentialKey())
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .orElseThrow(() -> new IOException("No personal access token is stored"));
        final Instant expiresAt =
                expiration
                        .find(
                                endpoint == null ? GitHub.apiEndpoint(connection.host()) : endpoint,
                                token)
                        .orElse(null);
        LOG.trace(
                "PAT expiration metadata loaded: {}, host={}, tokenFingerprint={}, expires={}",
                connection.name(),
                connection.host(),
                GitHubTokenExpiration.tokenFingerprint(token),
                expiresAt);
        return new GitHubToken(token, expiresAt, false);
    }
}
