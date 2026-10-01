package com.jagent.desktop.services.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GitHubTokenExpirationTest {
    private static final String ISO_EXPIRATION = "2026-10-01T12:34:56Z";

    @Test
    void parsesIsoInstantHeader() throws IOException {
        assertEquals(
                Instant.parse(ISO_EXPIRATION),
                GitHubTokenExpiration.parseExpiration(ISO_EXPIRATION),
                "ISO-8601 expiration should parse");
    }

    @Test
    void parsesGitHubUtcHeaderFormat() throws IOException {
        assertEquals(
                Instant.parse(ISO_EXPIRATION),
                GitHubTokenExpiration.parseExpiration("2026-10-01 12:34:56 UTC"),
                "GitHub header expiration format should parse");
    }

    @Test
    void rejectsInvalidHeaderFormat() {
        assertThrows(
                IOException.class,
                () -> GitHubTokenExpiration.parseExpiration("not-a-timestamp"),
                "invalid expiration header should fail");
    }

    @Test
    void httpLookupReadsExpirationHeader() throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/user",
                exchange -> {
                    exchange.getResponseHeaders()
                            .add("GitHub-Authentication-Token-Expiration", ISO_EXPIRATION);
                    exchange.sendResponseHeaders(200, -1);
                    exchange.close();
                });
        server.start();
        try {
            final String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            final Optional<Instant> expiration =
                    GitHubTokenExpiration.http().find(endpoint, "token-value");
            assertTrue(expiration.isPresent(), "token expiration should be present");
            assertEquals(
                    Instant.parse(ISO_EXPIRATION),
                    expiration.orElseThrow(),
                    "expiration should parse from response header");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpLookupThrowsWhenGitHubReturnsFailureStatus() throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/user",
                exchange -> {
                    exchange.sendResponseHeaders(401, -1);
                    exchange.close();
                });
        server.start();
        try {
            final String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            final IOException exception =
                    assertThrows(
                            IOException.class,
                            () -> GitHubTokenExpiration.http().find(endpoint, "token-value"),
                            "non-success status should fail");
            assertNotNull(exception.getMessage(), "failure should include a message");
            assertTrue(
                    exception.getMessage().contains("401"),
                    "failure should include the HTTP status");
        } finally {
            server.stop(0);
        }
    }
}
