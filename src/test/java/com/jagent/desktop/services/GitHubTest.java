package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.test.TestGitRepository;
import java.io.IOException;
import java.nio.file.Path;
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
    void loadingAuthoredRequestsRequiresAGitHubRemote(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);

        assertThrows(
                IOException.class,
                () -> GitHub.loadForProject(ProjectId.create(), project(directory)),
                "a project without a GitHub remote should fail before invoking gh");
    }

    @Test
    void loadingReviewRequestsRequiresAGitHubRemote(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);

        assertThrows(
                IOException.class,
                () -> GitHub.loadReviewRequestedForProject(ProjectId.create(), project(directory)),
                "a project without a GitHub remote should fail before invoking gh");
    }

    @Test
    void loadingCurrentRequestReportsMissingWorktree(@TempDir final Path directory) {
        final Project project = project(directory);

        assertThrows(
                IOException.class,
                () -> GitHub.loadCurrent(project, directory.resolve("missing")),
                "a missing worktree should fail without attempting a request lookup");
    }

    @Test
    void loadingCurrentRequestReportsGitHubCliErrors(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);

        final IOException exception =
                assertThrows(
                        IOException.class,
                        () -> GitHub.loadCurrent(project(directory), directory),
                        "a repository without a pull request should report the CLI error");

        assertNotNull(exception.getMessage(), "CLI errors should include a message");
        assertFalse(exception.getMessage().isBlank(), "CLI errors should include details");
    }

    private static Project project(final Path directory) {
        return new Project("Test", directory.toString(), null);
    }
}
