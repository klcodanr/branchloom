package com.jagent.desktop.services;

import com.jagent.desktop.models.GitHubConnection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Acquires the temporary token managed by the GitHub CLI. */
public final class CliTokenProvider implements GitHubTokenProvider {
    private final GitHubTokenExpiration expiration;

    public CliTokenProvider() {
        this(GitHubTokenExpiration.http());
    }

    public CliTokenProvider(final GitHubTokenExpiration expiration) {
        this.expiration = expiration;
    }

    @Override
    public GitHubToken acquire(final GitHubConnection connection)
            throws IOException, InterruptedException {
        final List<String> command = new ArrayList<>();
        command.add("gh");
        command.add("auth");
        command.add("token");
        command.add("--hostname");
        command.add(connection.host());
        final String user = connection.user();
        if (user != null && !user.isBlank()) {
            command.add("--user");
            command.add(user);
        }
        final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        final String output =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0 || output.isBlank()) {
            throw new IOException("GitHub CLI could not provide a token");
        }
        Optional<Instant> expiresAt;
        try {
            expiresAt = expiration.find(GitHub.apiEndpoint(connection.host()), output);
        } catch (IOException ignored) {
            expiresAt = Optional.empty();
        }
        return new GitHubToken(
                output,
                expiresAt.orElse(Instant.now().plusSeconds(Duration.ofHours(1).getSeconds())),
                true);
    }
}
