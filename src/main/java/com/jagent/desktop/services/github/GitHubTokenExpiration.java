package com.jagent.desktop.services.github;

import com.jagent.desktop.services.JsonLogging;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Reads GitHub's token expiration response metadata. */
@FunctionalInterface
public interface GitHubTokenExpiration {
    Optional<Instant> find(String endpoint, String token) throws IOException;

    static GitHubTokenExpiration http() {
        return (endpoint, token) -> {
            final HttpURLConnection connection =
                    (HttpURLConnection) URI.create(endpoint + "/user").toURL().openConnection();
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestMethod("GET");
            try {
                final int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IOException("GitHub authentication request failed: " + status);
                }
                final String value =
                        connection.getHeaderField("GitHub-Authentication-Token-Expiration");
                final Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("endpoint", endpoint);
                metadata.put("status", status);
                metadata.put("value", value);
                JsonLogging.debug(
                        GitHubTokenExpiration.class,
                        "GitHub token expiration header read",
                        metadata);
                return Optional.of(parseExpiration(value));
            } finally {
                connection.disconnect();
            }
        };
    }

    static Instant parseExpiration(final String value) throws IOException {
        final String trimmed = value == null ? "" : value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // fall through to GitHub's response-header format
        }
        try {
            final String withoutUtc =
                    trimmed.endsWith(" UTC") ? trimmed.substring(0, trimmed.length() - 4) : trimmed;
            final LocalDateTime parsed =
                    LocalDateTime.parse(
                            withoutUtc, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return parsed.toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            JsonLogging.warn(
                    GitHubTokenExpiration.class,
                    "Could not parse GitHub token expiration header",
                    Map.of("value", trimmed, "reason", exception.getMessage()));
            throw new IOException("GitHub returned an invalid token expiration", exception);
        }
    }
}
