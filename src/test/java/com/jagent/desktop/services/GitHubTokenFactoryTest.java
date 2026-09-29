package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jagent.desktop.models.GitHubConnection;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GitHubTokenFactoryTest {
    private static final String PAT_TOKEN = "pat";
    private static final String CLI_TOKEN = "cli";
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private static final GitHubConnection PAT =
            new GitHubConnection("pat", "PAT", "github.com", null, true, "github:pat");
    private static final GitHubConnection CLI =
            new GitHubConnection("github-cli", "CLI", "github.com", null, false, null);

    @Test
    void cachesTokensByConnection() throws IOException, InterruptedException {
        final AtomicInteger calls = new AtomicInteger();
        final GitHubTokenFactory factory =
                factory(
                        connection -> {
                            calls.incrementAndGet();
                            return new GitHubToken(PAT_TOKEN, NOW.plusSeconds(7200), false);
                        },
                        connection -> new GitHubToken(CLI_TOKEN, null, true));

        assertEquals(PAT_TOKEN, factory.token(PAT), "the initial PAT should be returned");
        assertEquals(PAT_TOKEN, factory.token(PAT), "a valid PAT should be cached");
        assertEquals(1, calls.get(), "the provider should only be called once");
    }

    @Test
    void refreshesRenewableTokenInsideWindow() throws IOException, InterruptedException {
        final AtomicInteger calls = new AtomicInteger();
        final GitHubTokenFactory factory =
                factory(
                        connection -> new GitHubToken(PAT_TOKEN, NOW.plusSeconds(7200), false),
                        connection ->
                                new GitHubToken(
                                        "cli-" + calls.incrementAndGet(),
                                        NOW.plusSeconds(30),
                                        true));

        assertEquals("cli-1", factory.token(CLI), "the first CLI token should be returned");
        assertEquals("cli-2", factory.token(CLI), "an expiring CLI token should be refreshed");
    }

    @Test
    void rejectsNonRenewableTokenInsideWindow() throws IOException, InterruptedException {
        final GitHubTokenFactory factory =
                factory(
                        connection -> new GitHubToken("pat", NOW.plusSeconds(30), false),
                        connection -> new GitHubToken("cli", null, true));

        assertThrows(
                GitHubAuthException.class,
                () -> factory.token(PAT),
                "a PAT cannot be renewed automatically");
    }

    @Test
    void wrapsProviderFailureAsAuthenticationFailure() {
        final GitHubTokenFactory factory =
                factory(
                        connection -> {
                            throw new IOException("expired");
                        },
                        connection -> new GitHubToken(CLI_TOKEN, null, true));

        assertThrows(
                GitHubAuthException.class,
                () -> factory.token(PAT),
                "provider failures should become authentication failures");
    }

    @Test
    void allowsCliTokenWithoutExpiration() throws IOException, InterruptedException {
        final GitHubTokenFactory factory =
                factory(
                        connection -> new GitHubToken(PAT_TOKEN, NOW.plusSeconds(7200), false),
                        connection -> new GitHubToken(CLI_TOKEN, null, true));

        assertEquals(CLI_TOKEN, factory.token(CLI), "CLI tokens may have unknown expiration");
    }

    @Test
    void doesNotShareCliTokensBetweenUsers() throws IOException, InterruptedException {
        final AtomicInteger calls = new AtomicInteger();
        final GitHubConnection otherUser =
                new GitHubConnection("github-cli", "CLI", "github.com", "other", false, null);
        final GitHubTokenFactory factory =
                factory(
                        connection -> new GitHubToken(PAT_TOKEN, NOW.plusSeconds(7200), false),
                        connection ->
                                new GitHubToken(CLI_TOKEN + calls.incrementAndGet(), null, true));

        assertEquals("cli1", factory.token(CLI), "the first user's token should be used");
        assertEquals("cli2", factory.token(otherUser), "the second user's token should be used");
        assertEquals(2, calls.get(), "tokens for different users must not share a cache entry");
    }

    private static GitHubTokenFactory factory(
            final GitHubTokenProvider pat, final GitHubTokenProvider cli) {
        return new GitHubTokenFactory(pat, cli, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
