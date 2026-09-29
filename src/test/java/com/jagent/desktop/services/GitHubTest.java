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
    @Test
    void authFormatsHostAndUser() {
        assertEquals(
                "alice (github.example)",
                new GitHub.Auth("github.example", "alice").toString(),
                "auth should format host and user");
    }

    @Test
    void authFormatsPersonalAccessTokenWithoutExposingToken() {
        assertEquals(
                "Personal access token (github.example)",
                new GitHub.Auth("github.example", null, "pat-connection", null).toString(),
                "auth should not expose the token");
    }

    @Test
    void configuredAuthsIncludesDefaultCliOption() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var auths = GitHub.configuredAuths(state);
        assertFalse(auths.isEmpty(), "default auth should be available");
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
