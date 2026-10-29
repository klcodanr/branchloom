package com.jagent.desktop.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.models.board.AgentSessionBoardItem;
import com.jagent.desktop.models.board.sources.AgentSessionConfig;
import com.jagent.desktop.models.board.sources.GithubIssuesConfig;
import com.jagent.desktop.models.board.sources.JiraConfig;
import com.jagent.desktop.models.board.sources.PullRequestConfig;
import com.jagent.desktop.models.board.sources.SourceConfigs;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.ui.Defaults;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardInstanceStateTest {
    @Test
    void emptySourcesCompleteWithNoItems()
            throws java.util.concurrent.ExecutionException, InterruptedException {
        final BoardInstanceState state =
                new BoardInstanceState(
                        new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of()),
                        new BackgroundJobs());

        final var result = state.fetch("boards", SourceConfigs.empty()).get();

        assertTrue(result.isEmpty(), "disabled board sources should produce no items");
        assertEquals(result, state.get("boards").orElseThrow(), "results should be cached");
    }

    @Test
    void loadsAgentSessionsWithFallbackValuesAndActivityState()
            throws java.util.concurrent.ExecutionException, InterruptedException {
        final ProjectId projectId = ProjectId.create();
        final SessionId idleId = SessionId.create();
        final SessionId activeId = SessionId.create();
        final SessionId missingProjectId = SessionId.create();
        final TerminalId terminalId = TerminalId.create();
        final Project project = new Project("Demo", "/tmp/demo", null);
        final Session idle =
                new Session(projectId, "Idle", null, null, null, Instant.EPOCH, List.of());
        final Session active =
                new Session(
                        projectId,
                        "Active",
                        "agent",
                        "prompt",
                        "/tmp/worktree",
                        Instant.EPOCH,
                        List.of(terminalId));
        final Session missingProject =
                new Session(
                        ProjectId.create(),
                        "Missing project",
                        null,
                        null,
                        null,
                        Instant.EPOCH,
                        List.of());
        final AppState appState =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), project),
                        Map.of(
                                idleId.value().toString(), idle,
                                activeId.value().toString(), active,
                                missingProjectId.value().toString(), missingProject),
                        Map.of());
        final BoardInstanceState state = new BoardInstanceState(appState, new BackgroundJobs());
        final SourceConfigs sources =
                new SourceConfigs(
                        new AgentSessionConfig(true),
                        new GithubIssuesConfig(false, ""),
                        new PullRequestConfig(false, ""),
                        new JiraConfig(false, ""));

        final List<AgentSessionBoardItem> items =
                state.fetch("sessions", sources).get().stream()
                        .map(item -> assertInstanceOf(AgentSessionBoardItem.class, item))
                        .toList();

        assertEquals(3, items.size(), "enabled agent sessions should load every session");
        final AgentSessionBoardItem idleItem =
                items.stream()
                        .filter(item -> item.sessionId().equals(idleId))
                        .findFirst()
                        .orElseThrow();
        assertEquals("idle", idleItem.state(), "sessions without terminals should be idle");
        assertEquals("", idleItem.agent(), "null agents should use an empty value");
        assertEquals("Demo", idleItem.project(), "session project names should be included");

        final AgentSessionBoardItem activeItem =
                items.stream()
                        .filter(item -> item.sessionId().equals(activeId))
                        .findFirst()
                        .orElseThrow();
        assertEquals("active", activeItem.state(), "sessions with terminals should be active");
        assertEquals("agent", activeItem.agent(), "agent names should be preserved");
        assertEquals("/tmp/worktree", activeItem.branch(), "worktree paths should be preserved");
        assertEquals("prompt", activeItem.description(), "prompts should be preserved");

        final AgentSessionBoardItem missingItem =
                items.stream()
                        .filter(item -> item.sessionId().equals(missingProjectId))
                        .findFirst()
                        .orElseThrow();
        assertEquals("", missingItem.project(), "missing projects should use an empty name");
    }
}
