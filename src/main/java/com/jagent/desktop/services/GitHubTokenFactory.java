package com.jagent.desktop.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jagent.desktop.models.GitHubConnection;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Selects GitHub credential providers and caches credentials for the application session. */
public final class GitHubTokenFactory {
    private static final Duration REFRESH_WINDOW = Duration.ofMinutes(5);
    private static final Logger LOG = LoggerFactory.getLogger(GitHubTokenFactory.class);

    private final GitHubTokenProvider personalAccessTokenProvider;
    private final GitHubTokenProvider cliTokenProvider;
    private final Clock clock;
    private static final Duration CACHE_EXPIRATION = Duration.ofHours(1);
    private static final long CACHE_MAXIMUM_SIZE = 512;
    private final Cache<String, GitHubToken> tokens =
            Caffeine.newBuilder()
                    .maximumSize(CACHE_MAXIMUM_SIZE)
                    .expireAfterWrite(CACHE_EXPIRATION)
                    .build();

    public GitHubTokenFactory(
            final GitHubTokenProvider personalAccessTokenProvider,
            final GitHubTokenProvider cliTokenProvider,
            final Clock clock) {
        this.personalAccessTokenProvider = personalAccessTokenProvider;
        this.cliTokenProvider = cliTokenProvider;
        this.clock = clock;
    }

    public String token(final GitHubConnection connection)
            throws IOException, InterruptedException {
        final GitHubToken cached = tokens.getIfPresent(cacheKey(connection));
        if (cached != null) {
            return useCached(connection, cached);
        }
        return acquire(connection, null);
    }

    private String useCached(final GitHubConnection connection, final GitHubToken cached)
            throws IOException, InterruptedException {
        if (usable(cached)) {
            LOG.trace(
                    "Using cached GitHub token: connection={}, host={}, expiresAt={}",
                    connection.name(),
                    connection.host(),
                    cached.expiresAt());
            return cached.value();
        }
        if (!cached.renewable()) {
            LOG.trace(
                    "Cached GitHub token is unusable and non-renewable: connection={}, host={}, expiresAt={}",
                    connection.name(),
                    connection.host(),
                    cached.expiresAt());
            throw new GitHubAuthException(connection.name(), cached.expiresAt());
        }
        LOG.trace(
                "Refreshing cached renewable GitHub token: connection={}, host={}, expiresAt={}",
                connection.name(),
                connection.host(),
                cached.expiresAt());
        return acquire(connection, cached);
    }

    private String acquire(final GitHubConnection connection, final GitHubToken cached)
            throws IOException, InterruptedException {
        final GitHubToken refreshed;
        try {
            refreshed = provider(connection).acquire(connection);
        } catch (IOException | InterruptedException exception) {
            LOG.trace(
                    "GitHub token provider failed: connection={}, host={}",
                    connection.name(),
                    connection.host(),
                    exception);
            throw new GitHubAuthException(
                    connection.name(), cached == null ? null : cached.expiresAt(), exception);
        }
        if (!validForCaching(refreshed)) {
            LOG.trace(
                    "GitHub token rejected after acquisition: connection={}, host={}, expiresAt={}, renewable={}",
                    connection.name(),
                    connection.host(),
                    refreshed.expiresAt(),
                    refreshed.renewable());
            throw new GitHubAuthException(connection.name(), refreshed.expiresAt());
        }
        LOG.trace(
                "GitHub token accepted: connection={}, host={}, expiresAt={}, renewable={}",
                connection.name(),
                connection.host(),
                refreshed.expiresAt(),
                refreshed.renewable());
        tokens.put(cacheKey(connection), refreshed);
        return refreshed.value();
    }

    private String cacheKey(final GitHubConnection connection) {
        return connection.id()
                + "|"
                + connection.host()
                + "|"
                + connection.user()
                + "|"
                + connection.usePersonalAccessToken();
    }

    private GitHubTokenProvider provider(final GitHubConnection connection) {
        return connection.usePersonalAccessToken() ? personalAccessTokenProvider : cliTokenProvider;
    }

    private boolean usable(final GitHubToken token) {
        final Instant expiresAt = token.expiresAt();
        return expiresAt == null || expiresAt.isAfter(Instant.now(clock).plus(REFRESH_WINDOW));
    }

    private boolean validForCaching(final GitHubToken token) {
        return token.value() != null
                && !token.value().isBlank()
                && (token.renewable() || (token.expiresAt() != null && usable(token)));
    }
}
