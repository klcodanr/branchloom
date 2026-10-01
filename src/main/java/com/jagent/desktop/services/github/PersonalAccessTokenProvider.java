package com.jagent.desktop.services.github;

import com.jagent.desktop.models.github.Credential;
import com.jagent.desktop.models.github.GitHubToken;
import com.jagent.desktop.models.github.PatCredential;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads a PAT from the OS keyring and requires its GitHub expiration metadata. */
public final class PersonalAccessTokenProvider implements GitHubTokenProvider<PatCredential> {
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
    public GitHubToken acquire(final Credential credential) throws IOException {
        if (!(credential instanceof PatCredential patCredential)) {
            throw new IOException("Personal access token credential is required");
        }
        final String token =
                credentials
                        .get(patCredential.credentialId())
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .orElseThrow(() -> new IOException("No personal access token is stored"));
        final Instant expiresAt =
                expiration
                        .find(
                                endpoint == null
                                        ? GitHubAuth.apiEndpoint(credential.host())
                                        : endpoint,
                                token)
                        .orElse(null);
        LOG.trace(
                "PAT expiration metadata loaded: {}, host={}, expires={}",
                credential.name(),
                credential.host(),
                expiresAt);
        return new GitHubToken(token, expiresAt, false);
    }
}
