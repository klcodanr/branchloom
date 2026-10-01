package com.jagent.desktop.services.github;

import com.jagent.desktop.models.github.CliCredential;
import com.jagent.desktop.models.github.Credential;
import com.jagent.desktop.models.github.GitHubToken;
import com.jagent.desktop.services.PlatformCommands;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Acquires the temporary token managed by the GitHub CLI. */
public final class CliTokenProvider implements GitHubTokenProvider<CliCredential> {

    @Override
    public GitHubToken acquire(final Credential connection) throws IOException {
        if (!(connection instanceof CliCredential cliCredential)) {
            throw new IOException("GitHub CLI credential is required");
        }
        final List<String> command = new ArrayList<>();
        command.add(PlatformCommands.executable("gh"));
        command.add("auth");
        command.add("token");
        command.add("--hostname");
        command.add(connection.host());
        if (!cliCredential.user().isBlank()) {
            command.add("--user");
            command.add(cliCredential.user());
        }
        try {
            final Process process =
                    PlatformCommands.prepare(new ProcessBuilder(command))
                            .redirectErrorStream(true)
                            .start();
            final String output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                            .trim();
            if (process.waitFor() != 0 || output.isBlank()) {
                throw new IOException("GitHub CLI could not provide a token");
            }
            return new GitHubToken(
                    output, Instant.now().plusSeconds(Duration.ofHours(1).getSeconds()), true);
        } catch (InterruptedException ie) {
            throw new IOException("GitHub CLI token acquisition was interrupted", ie);
        }
    }
}
