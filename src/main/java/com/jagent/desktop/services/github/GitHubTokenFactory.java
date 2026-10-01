package com.jagent.desktop.services.github;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jagent.desktop.models.github.CliCredential;
import com.jagent.desktop.models.github.Credential;
import com.jagent.desktop.models.github.GitHubToken;
import com.jagent.desktop.models.github.PatCredential;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Selects GitHub credential providers and caches credentials for the application session. */
public final class GitHubTokenFactory {
    private static final Duration REFRESH_WINDOW = Duration.ofMinutes(5);
    private static final Logger LOG = LoggerFactory.getLogger(GitHubTokenFactory.class);

    private final GitHubTokenProvider<PatCredential> personalAccessTokenProvider;
    private final GitHubTokenProvider<CliCredential> cliTokenProvider;

    private static final Duration CACHE_EXPIRATION = Duration.ofHours(1);
    private static final long CACHE_MAXIMUM_SIZE = 512;
    private final Cache<String, GitHubToken> tokens =
            Caffeine.newBuilder()
                    .maximumSize(CACHE_MAXIMUM_SIZE)
                    .expireAfterWrite(CACHE_EXPIRATION)
                    .build();

    public GitHubTokenFactory(
            final GitHubTokenProvider<PatCredential> personalAccessTokenProvider,
            final GitHubTokenProvider<CliCredential> cliTokenProvider) {
        this.personalAccessTokenProvider = personalAccessTokenProvider;
        this.cliTokenProvider = cliTokenProvider;
    }

    public static GitHubTokenFactory create() {
        CredentialStore credentialStore;
        try {
            credentialStore = new KeyringCredentialStore();
        } catch (RuntimeException exception) {
            LOG.warn("OS keyring unavailable; using file system credential store", exception);
            credentialStore = new FileSystemCredentialStore();
        }
        return new GitHubTokenFactory(
                new PersonalAccessTokenProvider(credentialStore, GitHubTokenExpiration.http()),
                new CliTokenProvider());
    }

    public String getToken(final Credential credential) throws IOException {
        final GitHubToken cached = tokens.getIfPresent(credential.id());
        if (cached != null) {
            return useCached(credential, cached);
        }
        return acquire(credential);
    }

    private String useCached(final Credential credential, final GitHubToken cached)
            throws IOException {
        final Instant expiresAt = cached.expiresAt();
        if (expiresAt != null && expiresAt.isAfter(Instant.now().plus(REFRESH_WINDOW))) {
            return cached.value();
        }
        if (!cached.renewable()) {
            throw new IOException(
                    "Cached GitHub token: "
                            + credential.id()
                            + " is not renewable and has expired.");
        }
        LOG.trace(
                "Refreshing cached renewable GitHub token: credential={}, expiresAt={}",
                credential.id(),
                cached.expiresAt());
        return acquire(credential);
    }

    private String acquire(final Credential credential) throws IOException {
        final GitHubToken refreshed;
        try {
            refreshed = provider(credential).acquire(credential);
        } catch (IOException exception) {
            LOG.warn("GitHub token provider failed: credential={}", credential.id(), exception);
            throw new IOException(
                    "Failed to acquire GitHub token for credential: " + credential.id(), exception);
        }
        if (!usable(refreshed)) {
            throw new IOException(
                    "Acquired GitHub token for credential: " + credential.id() + " is not usable.");
        }
        if (validForCaching(refreshed)) {
            tokens.put(credential.id(), refreshed);
        }
        return refreshed.value();
    }

    private GitHubTokenProvider<? extends Credential> provider(final Credential credential) {
        if (credential instanceof PatCredential) {
            return personalAccessTokenProvider;
        }
        return cliTokenProvider;
    }

    private boolean validForCaching(final GitHubToken token) {
        return token.value() != null
                && !token.value().isBlank()
                && (token.renewable() || (token.expiresAt() != null && usable(token)));
    }

    private boolean usable(final GitHubToken token) {
        final Instant expiresAt = token.expiresAt();
        return expiresAt != null && expiresAt.isAfter(Instant.now().plus(REFRESH_WINDOW));
    }
}
