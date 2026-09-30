package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.test.TestGitRepository;
import com.jagent.desktop.ui.Defaults;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitHubTest {
    private static final String GITHUB_EXAMPLE = "github.example";

    @Test
    void authFormatsHostAndUser() {
        assertEquals(
                "alice (github.example)",
                new GitHub.Auth(GITHUB_EXAMPLE, "alice").toString(),
                "auth should format host and user");
    }

    @Test
    void authFormatsPersonalAccessTokenWithoutExposingToken() {
        assertEquals(
                "Personal access token (github.example)",
                new GitHub.Auth(GITHUB_EXAMPLE, null, "pat-connection", null).toString(),
                "auth should not expose the token");
    }

    @Test
    void configuredAuthsIncludesDefaultCliOption() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var auths = GitHub.configuredAuths(state);
        assertFalse(auths.isEmpty(), "default auth should be available");
    }

    @Test
    void apiEndpointUsesGitHubComForDefaultHostCaseInsensitively() {
        assertEquals(
                "https://api.github.com",
                GitHub.apiEndpoint("github.com"),
                "default GitHub host should use public API endpoint");
        assertEquals(
                "https://api.github.com",
                GitHub.apiEndpoint("GitHub.Com"),
                "default host matching should be case-insensitive");
    }

    @Test
    void apiEndpointUsesEnterprisePathForCustomHostAndProjectFallback() {
        assertEquals(
                "https://github.example/api/v3",
                GitHub.apiEndpoint(GITHUB_EXAMPLE),
                "custom host should map to enterprise API path");
        assertEquals(
                "https://api.github.com",
                GitHub.apiEndpoint(new Project("Test", "/tmp/test", null)),
                "project without host should fall back to github.com endpoint");
    }

    @Test
    void authConnectionDistinguishesCliAndPatConnections() {
        final GitHub.Auth cli =
                new GitHub.Auth("github.com", "alice", "github-cli:github.com:alice");
        final GitHub.Auth token =
                new GitHub.Auth(GITHUB_EXAMPLE, "alice", "corp-token", "Corporate token");

        assertFalse(!cli.isCli(), "CLI-prefixed connection ids should be treated as CLI auth");
        assertFalse(token.isCli(), "non-CLI connection ids should be treated as PAT auth");
    }

    @Test
    void loadingRequestsRequiresAGitHubRemote(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        assertThrows(
                IOException.class,
                () -> GitHub.loadForProject(ProjectId.create(), project(directory), "author:@me"),
                "a project without a GitHub remote should fail before invoking gh");
    }

    @Test
    void loadingIssuesRequiresAGitHubRemote(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        assertThrows(
                IOException.class,
                () -> GitHub.loadIssuesForProject(project(directory)),
                "issue loading should fail before invoking gh without a GitHub remote");
    }

    @Test
    void loadingCurrentRequestReportsMissingWorktree(@TempDir final Path directory) {
        final Project project = project(directory);
        assertThrows(
                IOException.class,
                () -> GitHub.pullRequest(ProjectId.create(), project, directory.resolve("missing")),
                "a missing worktree should fail request lookup");
    }

    @Test
    void loadingCurrentRequestReportsLookupErrors(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final IOException exception =
                assertThrows(
                        IOException.class,
                        () -> GitHub.pullRequest(ProjectId.create(), project(directory), directory),
                        "a repository without a pull request should report lookup errors");
        assertNotNull(exception.getMessage(), "errors should include a message");
    }

    private static Project project(final Path directory) {
        return new Project("Test", directory.toString(), null);
    }
}
