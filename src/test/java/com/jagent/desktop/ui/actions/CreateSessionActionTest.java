package com.jagent.desktop.ui.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.test.AsyncTestSupport;
import com.jagent.desktop.test.TestAppState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CreateSessionActionTest {
    private static final long AGENT_START_TIMEOUT_NANOS = 10_000_000_000L;
    @TempDir private Path tempDirectory;

    @Test
    void startsAgentAfterStartupCompletes() throws IOException, InterruptedException {
        final Path repository = tempDirectory.resolve("repository");
        Files.createDirectories(repository);
        com.jagent.desktop.test.TestGitRepository.initialize(repository);
        final Path worktree = tempDirectory.resolve("worktree-{sessionSlug}");
        final Path notes = tempDirectory.resolve("worktree-feature/.branchloom/notes.md");
        final Path marker = tempDirectory.resolve("agent-started");
        final String prompt = "Review 'the changes'";
        final Agent agent =
                new Agent(
                        "Test agent",
                        "test \"$(cat .branchloom/notes.md)\" = setup"
                                + " && printf '%s' {prompt} > "
                                + PlatformCommands.shellQuote(marker.toString()));
        final Project project =
                new Project(
                        com.jagent.desktop.models.ProjectId.create(),
                        "Demo",
                        repository.toString(),
                        null,
                        null,
                        worktree.toString(),
                        null,
                        List.of(
                                "sleep 1; mkdir -p .branchloom; printf setup > .branchloom/notes.md"),
                        List.of(),
                        ".branchloom/notes.md",
                        null);
        final AppState state = TestAppState.empty();
        final var projectId = state.addProject(project);
        final var coordinator = TestAppState.controller(state);
        final var backgroundJobs = TestAppState.backgroundJobs(state);
        new CreateSessionAction(coordinator, state, backgroundJobs, TestAppState.dialogService())
                .addSession(
                        projectId,
                        new com.jagent.desktop.ui.dialogs.NewSessionDialog.Request(
                                "Feature", agent, prompt));

        AsyncTestSupport.await(
                () -> state.sessions().size() == 1 && state.terminals().size() == 1,
                "session and terminal should be persisted before setup completes");
        final var terminal = state.terminals().values().iterator().next();
        assertEquals(
                "test \"$(cat .branchloom/notes.md)\" = setup && printf '%s' "
                        + PlatformCommands.shellQuote(prompt)
                        + " > "
                        + PlatformCommands.shellQuote(marker.toString()),
                terminal.command(),
                "agent command should contain the expanded prompt");
        AsyncTestSupport.await(() -> Files.exists(notes), "startup command should create notes");
        AsyncTestSupport.await(
                () -> Files.exists(marker),
                "agent should start after setup",
                AGENT_START_TIMEOUT_NANOS);
        AsyncTestSupport.await(
                () -> !backgroundJobs.jobs().isEmpty(), "startup job should be tracked");
    }
}
