package com.jagent.desktop.services.github;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.github.Auth;
import com.jagent.desktop.models.github.CliCredential;
import com.jagent.desktop.models.github.Credential;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.PlatformCommands;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Resolves GitHub project auth selection, API endpoint, and token acquisition. */
public final class GitHubAuth {
    private static final String DEFAULT_HOST = "github.com";
    private static final List<CliCredential> DEFAULT_CLI_AUTH =
            List.of(new CliCredential(DEFAULT_HOST, "Default (active account)", ""));
    private static final Logger LOG = LoggerFactory.getLogger(GitHubAuth.class);
    private static final GitHubTokenFactory TOKEN_FACTORY = GitHubTokenFactory.create();

    public Auth getAuth(final Project project) throws IOException {
        final Credential selected =
                Optional.ofNullable(project.credential()).orElse(DEFAULT_CLI_AUTH.getFirst());
        final String host = Optional.ofNullable(selected.host()).orElse(DEFAULT_HOST);
        final String token = TOKEN_FACTORY.getToken(selected);
        return new Auth(host, token);
    }

    public List<Credential> listCredentials(final AppState state) {
        final List<Credential> auths = new ArrayList<>(cliAuths());
        auths.addAll(state.githubConnections().values());
        return List.copyOf(auths);
    }

    public static String apiEndpoint(final String host) {
        return host == null || DEFAULT_HOST.equalsIgnoreCase(host)
                ? "https://api.github.com"
                : "https://" + host + "/api/v3";
    }

    private List<CliCredential> cliAuths() {
        try {
            final Process process =
                    PlatformCommands.prepare(
                                    new ProcessBuilder(
                                            PlatformCommands.executable("gh"),
                                            "auth",
                                            "status",
                                            "--json",
                                            "hosts"))
                            .redirectErrorStream(true)
                            .start();
            final String output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                return DEFAULT_CLI_AUTH;
            }
            try {
                return parseCliAuths(output);
            } catch (RuntimeException ignored) {
                LOG.warn("Failed to parse CLI auths", ignored);
            }
        } catch (IOException | InterruptedException e) {
            LOG.warn("Failed to execute CLI auth command", e);
        }
        return DEFAULT_CLI_AUTH;
    }

    private List<CliCredential> parseCliAuths(final String output) {
        final JsonObject hosts =
                JsonParser.parseString(output).getAsJsonObject().getAsJsonObject("hosts");

        if (hosts == null || hosts.isEmpty()) {
            return DEFAULT_CLI_AUTH;
        }
        final List<CliCredential> auths = new ArrayList<>();
        for (final String host : hosts.keySet()) {
            final JsonElement entries = hosts.get(host);
            if (entries.isJsonArray()) {
                entries.getAsJsonArray()
                        .forEach(
                                entry -> {
                                    final JsonObject account = entry.getAsJsonObject();
                                    final JsonElement login = account.get("login");
                                    if (login != null && !login.isJsonNull()) {
                                        final String user = login.getAsString();
                                        auths.add(new CliCredential(host, host + ":" + user, user));
                                    }
                                });
            }
        }
        return auths.isEmpty() ? DEFAULT_CLI_AUTH : auths;
    }
}
