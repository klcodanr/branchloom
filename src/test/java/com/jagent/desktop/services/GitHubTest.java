package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.test.TestGitRepository;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    @Test
    void loadsGitHubDataFromCliOutput(@TempDir final Path directory)
            throws IOException, InterruptedException, ReflectiveOperationException {
        TestGitRepository.initialize(directory);
        TestGitRepository.run(
                directory, "git remote add origin git@github.com:adobe/branchloom.git");
        final Path bin = Files.createDirectory(directory.resolve("bin"));
        final Path gh = bin.resolve("gh");
        Files.writeString(
                gh,
                "#!/bin/sh\n"
                        + "case \"$1\" in\n"
                        + "  auth) printf 'github.example\\talice\\n' ;;\n"
                        + "  pr) printf '42\\tTitle\\tOPEN\\tAPPROVED\\tMERGEABLE\\t"
                        + "https://github.com/adobe/branchloom/pull/42\\tfalse\\t2\\t3\\tPASSING\\n' ;;\n"
                        + "  api) printf '42\\tTitle\\tbody\\tcomment\\turl\\tcreated\\tupdated\\tAPPROVED\\tMERGEABLE\\tfalse\\tauthor\\tfeature\\t2\\t3\\tPASSING\\n' ;;\n"
                        + "  *) exit 1 ;;\n"
                        + "esac\n",
                StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(
                gh,
                java.util.Set.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                        java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));

        final VarHandle discoveredPath =
                MethodHandles.privateLookupIn(PlatformCommands.class, MethodHandles.lookup())
                        .findStaticVarHandle(
                                PlatformCommands.class, "discoveredPath", String.class);
        final Object previousPath = discoveredPath.get();
        discoveredPath.set(bin + java.io.File.pathSeparator + System.getenv("PATH"));
        try {
            assertEquals(1, GitHub.configuredAuths().size(), "configured auth should parse");
            assertEquals(
                    1,
                    GitHub.loadForProject(ProjectId.create(), project(directory)).size(),
                    "pull requests should parse");
            assertEquals(
                    1,
                    GitHub.loadIssuesForProject(project(directory)).size(),
                    "issues should parse");
            final GitHub.PullRequestDetails current =
                    GitHub.loadCurrent(project(directory), directory);
            assertEquals(42, current.number(), "current pull request should parse");
            assertEquals("PASSING", current.checksStatus(), "checks status should parse");
        } finally {
            discoveredPath.set(previousPath);
        }
    }

    private static Project project(final Path directory) {
        return new Project("Test", directory.toString(), null);
    }
}
