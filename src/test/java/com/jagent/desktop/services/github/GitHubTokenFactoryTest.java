package com.jagent.desktop.services.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jagent.desktop.models.github.CliCredential;
import com.jagent.desktop.models.github.GitHubToken;
import com.jagent.desktop.models.github.PatCredential;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GitHubTokenFactoryTest {
    private static final String PAT_TOKEN = "pat";
    private static final String CLI_TOKEN = "cli";
    private static final Instant FAR_FUTURE = Instant.parse("2099-01-01T00:00:00Z");
    private static final PatCredential PAT =
            new PatCredential("pat-connection", "github.com", "PAT");
    private static final CliCredential CLI =
            new CliCredential("github.com", "github.com:alice", "alice");

    @Test
    void cachesTokensByCredentialId() throws IOException {
        final AtomicInteger calls = new AtomicInteger();
        final GitHubTokenFactory factory =
                factory(
                        credential -> {
                            calls.incrementAndGet();
                            return new GitHubToken(PAT_TOKEN, FAR_FUTURE, false);
                        },
                        credential -> new GitHubToken(CLI_TOKEN, FAR_FUTURE, true));

        assertEquals(PAT_TOKEN, factory.getToken(PAT), "the initial PAT should be returned");
        assertEquals(PAT_TOKEN, factory.getToken(PAT), "a valid PAT should be cached");
        assertEquals(1, calls.get(), "the provider should only be called once");
    }

    @Test
    void refreshesRenewableTokenInsideWindow() throws IOException {
        final GitHubTokenFactory factory =
                factory(
                        credential -> new GitHubToken(PAT_TOKEN, FAR_FUTURE, false),
                        credential -> new GitHubToken("cli", Instant.now().plusSeconds(30), true));

        assertThrows(
                IOException.class,
                () -> factory.getToken(CLI),
                "renewable tokens inside the refresh window are currently unusable");
    }

    @Test
    void rejectsNonRenewableTokenInsideWindow() {
        final GitHubTokenFactory factory =
                factory(
                        credential -> new GitHubToken("pat", Instant.now().plusSeconds(30), false),
                        credential -> new GitHubToken("cli", FAR_FUTURE, true));

        assertThrows(
                IOException.class,
                () -> factory.getToken(PAT),
                "a non-renewable token inside the refresh window should fail");
    }

    @Test
    void wrapsProviderFailureAsIOException() {
        final GitHubTokenFactory factory =
                factory(
                        credential -> {
                            throw new IOException("expired");
                        },
                        credential -> new GitHubToken(CLI_TOKEN, FAR_FUTURE, true));

        assertThrows(
                IOException.class,
                () -> factory.getToken(PAT),
                "provider failures should be wrapped as IO failures");
    }

    @Test
    void rejectsCliTokenWithoutExpiration() {
        final GitHubTokenFactory factory =
                factory(
                        credential -> new GitHubToken(PAT_TOKEN, FAR_FUTURE, false),
                        credential -> new GitHubToken(CLI_TOKEN, null, true));

        assertThrows(
                IOException.class,
                () -> factory.getToken(CLI),
                "tokens without expiration are currently treated as unusable");
    }

    @Test
    void doesNotShareCliTokensBetweenDistinctCredentialIds() throws IOException {
        final AtomicInteger calls = new AtomicInteger();
        final CliCredential otherUser = new CliCredential("github.com", "github.com:bob", "bob");
        final GitHubTokenFactory factory =
                factory(
                        credential -> new GitHubToken(PAT_TOKEN, FAR_FUTURE, false),
                        credential ->
                                new GitHubToken(
                                        CLI_TOKEN + calls.incrementAndGet(), FAR_FUTURE, true));

        assertEquals("cli1", factory.getToken(CLI), "the first user's token should be used");
        assertEquals(
                "cli2",
                factory.getToken(otherUser),
                "a distinct CLI credential should not reuse another cached token");
        assertEquals(2, calls.get(), "tokens for different IDs must not share a cache entry");
    }

    private static GitHubTokenFactory factory(
            final GitHubTokenProvider<PatCredential> pat,
            final GitHubTokenProvider<CliCredential> cli) {
        return new GitHubTokenFactory(pat, cli);
    }
}
