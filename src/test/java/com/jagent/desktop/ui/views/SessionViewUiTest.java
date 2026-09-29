package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.*;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import com.jagent.desktop.ui.layout.WorkspaceSplitPane;
import java.io.InvalidObjectException;
import java.nio.file.Path;
import java.util.Map;
import javax.swing.JTabbedPane;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionViewUiTest {
    private static final String ASSERTION_MESSAGE = "target view behavior should match";
    private static final String PROJECT_NAME = "Demo";
    private static final String SESSION_NAME = "Feature";
    private static final String SESSION_AGENT = "agent";
    private static final String SESSION_PROMPT = "prompt";
    private static final String SUCCESS_COMMAND = "true";

    @Test
    void buildsSummaryAndHandlesNoTerminalSelection(@TempDir final Path directory)
            throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId =
                state.addProject(new Project(PROJECT_NAME, directory.toString(), null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(
                                projectId,
                                SESSION_NAME,
                                SESSION_AGENT,
                                SESSION_PROMPT,
                                directory.toString()));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        final ActionContext context = new ActionContext(new ViewCoordinator(state), state, null);

        final SessionView view = GuiActionRunner.execute(() -> new SessionView(context));

        assertEquals(ViewId.SESSION, view.id(), ASSERTION_MESSAGE);
        assertEquals(SESSION_NAME, view.title(), ASSERTION_MESSAGE);
        final WorkspaceSplitPane split = (WorkspaceSplitPane) view.getComponent(1);
        assertEquals(1, ((JTabbedPane) split.getLeftComponent()).getTabCount(), ASSERTION_MESSAGE);
        assertNotNull(split.getRightComponent(), ASSERTION_MESSAGE);
        assertSame(view, view.render(), ASSERTION_MESSAGE);
        view.selectTerminal(0);
        view.openSummary();
        view.closeActiveTerminal();
        view.renameActiveTerminal();
        view.dispose();
    }

    @Test
    void opensTerminalUsingProjectPathWhenWorktreeIsMissing(@TempDir final Path directory)
            throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId =
                state.addProject(new Project(PROJECT_NAME, directory.toString(), null));
        final var sessionId =
                state.addSession(projectId, new Session(projectId, SESSION_NAME, null, null, null));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        final SessionView view =
                GuiActionRunner.execute(
                        () ->
                                new SessionView(
                                        new ActionContext(
                                                new ViewCoordinator(state), state, null)));

        GuiActionRunner.execute(view::createTerminal);

        final JTabbedPane tabs =
                (JTabbedPane) ((WorkspaceSplitPane) view.getComponent(1)).getLeftComponent();
        assertEquals(2, tabs.getTabCount(), ASSERTION_MESSAGE);
        assertEquals(1, tabs.getSelectedIndex(), ASSERTION_MESSAGE);
        view.dispose();
    }

    @Test
    void restoresSelectedTabPerSessionWhenSwitchingSessions(@TempDir final Path directory)
            throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId =
                state.addProject(new Project(PROJECT_NAME, directory.toString(), null));
        final var firstSessionId =
                state.addSession(
                        projectId,
                        new Session(projectId, "First Session", null, SESSION_PROMPT, null));
        final var secondSessionId =
                state.addSession(
                        projectId,
                        new Session(projectId, "Second Session", null, SESSION_PROMPT, null));
        state.addTerminal(
                firstSessionId, new Terminal(firstSessionId, "First Terminal", SUCCESS_COMMAND));
        state.addTerminal(
                secondSessionId, new Terminal(secondSessionId, "Second Terminal", SUCCESS_COMMAND));
        state.updateCurrentProject(projectId);
        final var coordinator = new ViewCoordinator(state);
        final ActionContext context = new ActionContext(coordinator, state, null);

        state.updateCurrentSession(firstSessionId);
        final SessionView firstSessionView =
                GuiActionRunner.execute(() -> new SessionView(context));
        final JTabbedPane firstTabs =
                (JTabbedPane)
                        ((WorkspaceSplitPane) firstSessionView.getComponent(1)).getLeftComponent();
        GuiActionRunner.execute(() -> firstTabs.setSelectedIndex(1));
        firstSessionView.dispose();

        state.updateCurrentSession(secondSessionId);
        final SessionView secondSessionView =
                GuiActionRunner.execute(() -> new SessionView(context));
        final JTabbedPane secondTabs =
                (JTabbedPane)
                        ((WorkspaceSplitPane) secondSessionView.getComponent(1)).getLeftComponent();
        assertEquals(0, secondTabs.getSelectedIndex(), ASSERTION_MESSAGE);
        GuiActionRunner.execute(() -> secondTabs.setSelectedIndex(1));
        secondSessionView.dispose();

        state.updateCurrentSession(firstSessionId);
        final SessionView reopenedFirstSessionView =
                GuiActionRunner.execute(() -> new SessionView(context));
        final JTabbedPane reopenedFirstTabs =
                (JTabbedPane)
                        ((WorkspaceSplitPane) reopenedFirstSessionView.getComponent(1))
                                .getLeftComponent();

        assertEquals(1, reopenedFirstTabs.getSelectedIndex(), ASSERTION_MESSAGE);
        reopenedFirstSessionView.dispose();
    }

    @Test
    void rejectsAgentSessionWithoutWorktree(@TempDir final Path directory)
            throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId =
                state.addProject(new Project(PROJECT_NAME, directory.toString(), null));
        final var sessionId =
                state.addSession(
                        projectId, new Session(projectId, SESSION_NAME, SESSION_AGENT, null, null));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        final ActionContext context = new ActionContext(new ViewCoordinator(state), state, null);

        assertThrows(IllegalStateException.class, () -> new SessionView(context));
    }

    @Test
    void keepsSummarySelectedForInvalidTerminalSelections(@TempDir final Path directory)
            throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId =
                state.addProject(new Project(PROJECT_NAME, directory.toString(), null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(
                                projectId,
                                SESSION_NAME,
                                SESSION_AGENT,
                                SESSION_PROMPT,
                                directory.toString()));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        final SessionView view =
                GuiActionRunner.execute(
                        () ->
                                new SessionView(
                                        new ActionContext(
                                                new ViewCoordinator(state), state, null)));
        final JTabbedPane tabs =
                (JTabbedPane) ((WorkspaceSplitPane) view.getComponent(1)).getLeftComponent();

        GuiActionRunner.execute(
                () -> {
                    view.selectTerminal(0);
                    view.selectTerminal(99);
                    view.openSummary();
                });

        assertEquals(0, tabs.getSelectedIndex(), ASSERTION_MESSAGE);
        assertEquals(null, state.currentTerminalId(), ASSERTION_MESSAGE);
    }

    @Test
    void restoresAgentTerminalAsUserShell(@TempDir final Path directory)
            throws InvalidObjectException, InterruptedException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId =
                state.addProject(new Project(PROJECT_NAME, directory.toString(), null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(
                                projectId,
                                SESSION_NAME,
                                SESSION_AGENT,
                                SESSION_PROMPT,
                                directory.toString()));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        final var coordinator = new ViewCoordinator(state);
        final ActionContext context = new ActionContext(coordinator, state, null);

        final SessionView initialView = GuiActionRunner.execute(() -> new SessionView(context));
        final var terminalId =
                GuiActionRunner.execute(
                        () -> {
                            final var id =
                                    state.addTerminal(
                                            sessionId,
                                            new Terminal(
                                                    sessionId,
                                                    "Agent 1",
                                                    "printf restored > "
                                                            + PlatformCommands.shellQuote(
                                                                    directory
                                                                            .resolve("restored")
                                                                            .toString())));
                            state.updateCurrentTerminal(id);
                            return id;
                        });
        assertEquals(1, state.sessions().get(sessionId).terminalIds().size(), ASSERTION_MESSAGE);
        assertTrue(
                java.nio.file.Files.isDirectory(
                        Path.of(state.sessions().get(sessionId).worktreePath())),
                ASSERTION_MESSAGE);
        assertEquals(sessionId, state.currentSessionId(), ASSERTION_MESSAGE);
        assertEquals(terminalId, state.currentTerminalId(), ASSERTION_MESSAGE);
        assertEquals(
                PlatformCommands.userShell(),
                SessionView.terminalDefinitionForRestore(
                                state.sessions().get(sessionId),
                                terminalId,
                                state.terminals().get(terminalId))
                        .command(),
                ASSERTION_MESSAGE);
        final SessionView createdView = GuiActionRunner.execute(() -> new SessionView(context));
        assertEquals(
                1,
                ((JTabbedPane)
                                ((WorkspaceSplitPane) createdView.getComponent(1))
                                        .getLeftComponent())
                        .getSelectedIndex(),
                ASSERTION_MESSAGE);
        GuiActionRunner.execute(() -> state.updateCurrentTerminal(null));
        final SessionView reopenedView = GuiActionRunner.execute(() -> new SessionView(context));

        assertEquals(
                1,
                ((JTabbedPane)
                                ((WorkspaceSplitPane) reopenedView.getComponent(1))
                                        .getLeftComponent())
                        .getSelectedIndex(),
                ASSERTION_MESSAGE);
        initialView.dispose();
        createdView.dispose();
        reopenedView.dispose();
    }

    @Test
    void sharedTerminalCloseRemovesProjectAndSessionTerminals(@TempDir final Path directory)
            throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId =
                state.addProject(new Project(PROJECT_NAME, directory.toString(), null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(
                                projectId,
                                SESSION_NAME,
                                SESSION_AGENT,
                                SESSION_PROMPT,
                                directory.toString()));
        final var projectTerminalId =
                state.addTerminal(new Terminal(null, projectId, "Project", SUCCESS_COMMAND));
        final var sessionTerminalId =
                state.addTerminal(sessionId, new Terminal(sessionId, "Session", SUCCESS_COMMAND));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        final ActionContext context = new ActionContext(new ViewCoordinator(state), state, null);

        final ProjectView projectView =
                GuiActionRunner.execute(
                        () -> new ProjectView(context, state.projects().get(projectId)));
        projectView.selectTerminal(1);
        projectView.closeActiveTerminal();
        assertFalse(state.terminals().containsKey(projectTerminalId), ASSERTION_MESSAGE);

        state.updateCurrentTerminal(null);
        final SessionView sessionView = GuiActionRunner.execute(() -> new SessionView(context));
        sessionView.selectTerminal(1);
        sessionView.closeActiveTerminal();
        assertFalse(state.terminals().containsKey(sessionTerminalId), ASSERTION_MESSAGE);

        projectView.dispose();
        sessionView.dispose();
    }
}
