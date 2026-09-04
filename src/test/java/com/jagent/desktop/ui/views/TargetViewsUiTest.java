package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.*;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.AppSettings;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.Tool;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.ui.Defaults;
import com.jagent.desktop.ui.components.SessionSummary;
import com.jagent.desktop.ui.components.WorkspaceSplitPane;
import java.io.InvalidObjectException;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import org.assertj.swing.edt.GuiActionRunnable;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TargetViewsUiTest {
    private static final String ASSERTION_MESSAGE = "target view behavior should match";
    private static final String PROJECT_NAME = "Demo";
    private static final String PROJECT_PATH = "/tmp";
    private static final String SESSION_NAME = "Feature";
    private static final String SESSION_AGENT = "agent";
    private static final String SESSION_PROMPT = "prompt";
    private static final String SUCCESS_COMMAND = "true";
    @TempDir private java.nio.file.Path tempDirectory;

    @Test
    void globalSettingsRendersConfiguredRowsAndSavesChanges() {
        final AppSettings settings =
                new AppSettings(
                        List.of(new Agent("Claude", "claude {prompt}", "claude")),
                        List.of("Approved"),
                        "Review {title}",
                        "System",
                        List.of(new Tool("Editor", "editor .")),
                        "{projectPath}/worktree");
        final AppState state = new AppState(settings, Map.of(), Map.of(), Map.of());
        final var coordinator = new ViewCoordinator(state);
        final var view = new GlobalSettingsView(new ActionContext(coordinator, state, null));

        final var rendered = GuiActionRunner.execute(view::render);
        final var scroll = (JScrollPane) rendered.getComponent(0);
        final var body = (JPanel) scroll.getViewport().getView();
        final var tabs = (JTabbedPane) body.getComponent(0);
        final var agents = (JPanel) tabs.getComponentAt(1);
        final var editors = (JPanel) tabs.getComponentAt(2);
        final JButton addAgent = SwingTestSupport.findButton(agents, "+  Add agent");
        final JButton addEditor = SwingTestSupport.findButton(editors, "+  Add editor");
        GuiActionRunner.execute(
                () -> {
                    addAgent.doClick();
                    addEditor.doClick();
                });

        final var actions = (JPanel) rendered.getComponent(1);
        final JButton save = (JButton) actions.getComponent(1);
        GuiActionRunner.execute((GuiActionRunnable) save::doClick);

        assertEquals(ViewId.HOME, coordinator.currentViewId(), ASSERTION_MESSAGE);
        assertEquals(
                "{projectPath}/worktree",
                state.appSettings().worktreeTemplate(),
                ASSERTION_MESSAGE);
        assertEquals(1, state.appSettings().agents().size(), ASSERTION_MESSAGE);
        assertEquals(1, state.appSettings().tools().size(), ASSERTION_MESSAGE);
    }

    @Test
    void globalSettingsCancelReturnsHomeWithoutChangingState() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var coordinator = new ViewCoordinator(state);
        final var view = new GlobalSettingsView(new ActionContext(coordinator, state, null));
        final var rendered = GuiActionRunner.execute(view::render);
        final var actions = (JPanel) rendered.getComponent(1);

        GuiActionRunner.execute(() -> ((JButton) actions.getComponent(0)).doClick());

        assertEquals(ViewId.HOME, coordinator.currentViewId(), ASSERTION_MESSAGE);
        assertEquals(
                Defaults.DEFAULT_WORKTREE_TEMPLATE,
                state.appSettings().worktreeTemplate(),
                ASSERTION_MESSAGE);
    }

    @Test
    void projectViewBuildsTabsAndSupportsViewOperations() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        final var coordinator = new ViewCoordinator(state);
        final var context = new ActionContext(coordinator, state, null);
        final var project = state.projects().get(projectId);

        final var view = GuiActionRunner.execute(() -> new ProjectView(context, project));

        assertEquals(ViewId.PROJECT, view.id(), ASSERTION_MESSAGE);
        assertEquals(PROJECT_NAME, view.title(), ASSERTION_MESSAGE);
        final var split = (WorkspaceSplitPane) view.getComponent(1);
        assertEquals(2, ((JTabbedPane) split.getLeftComponent()).getTabCount(), ASSERTION_MESSAGE);
        assertNull(split.getRightComponent(), ASSERTION_MESSAGE);
        assertSame(view, view.render(), ASSERTION_MESSAGE);
        assertTrue(!view.focusPullRequestSearch(), ASSERTION_MESSAGE);
        view.openSummary();
        view.selectTerminal(0);
        view.closeActiveTerminal();
        view.renameActiveTerminal();
        view.dispose();
    }

    @Test
    void projectViewDoesNotRenderSessionTerminals() throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(
                                projectId,
                                SESSION_NAME,
                                "test-agent",
                                SESSION_PROMPT,
                                PROJECT_PATH));
        state.addTerminal(sessionId, new Terminal(sessionId, "Shell", SUCCESS_COMMAND));
        state.addTerminal(
                sessionId, new Terminal(sessionId, projectId, "Dual owner", SUCCESS_COMMAND));
        final var context = new ActionContext(new ViewCoordinator(state), state, null);

        final var view =
                GuiActionRunner.execute(
                        () -> new ProjectView(context, state.projects().get(projectId)));
        GuiActionRunner.execute(() -> {});

        assertEquals(
                2,
                ((JTabbedPane) ((WorkspaceSplitPane) view.getComponent(1)).getLeftComponent())
                        .getTabCount(),
                "session terminals should not appear in project tabs");
        view.dispose();
    }

    @Test
    void sessionViewBuildsSummaryAndHandlesNoTerminalSelection() throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(
                                projectId,
                                SESSION_NAME,
                                SESSION_AGENT,
                                SESSION_PROMPT,
                                PROJECT_PATH));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        final var coordinator = new ViewCoordinator(state);
        final var context = new ActionContext(coordinator, state, null);

        final var view = GuiActionRunner.execute(() -> new SessionView(context));

        assertEquals(ViewId.SESSION, view.id(), ASSERTION_MESSAGE);
        assertEquals(SESSION_NAME, view.title(), ASSERTION_MESSAGE);
        final var split = (WorkspaceSplitPane) view.getComponent(1);
        assertEquals(1, ((JTabbedPane) split.getLeftComponent()).getTabCount(), ASSERTION_MESSAGE);
        assertNull(split.getRightComponent(), ASSERTION_MESSAGE);
        assertSame(view, view.render(), ASSERTION_MESSAGE);
        view.selectTerminal(0);
        view.openSummary();
        view.closeActiveTerminal();
        view.renameActiveTerminal();
        view.dispose();
    }

    @Test
    void sharedTerminalCloseRemovesProjectAndSessionTerminals() throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(
                                projectId,
                                SESSION_NAME,
                                SESSION_AGENT,
                                SESSION_PROMPT,
                                PROJECT_PATH));
        final var projectTerminalId =
                state.addTerminal(new Terminal(null, projectId, "Project", SUCCESS_COMMAND));
        final var sessionTerminalId =
                state.addTerminal(sessionId, new Terminal(sessionId, "Session", SUCCESS_COMMAND));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        final var context = new ActionContext(new ViewCoordinator(state), state, null);

        final var projectView =
                GuiActionRunner.execute(
                        () -> new ProjectView(context, state.projects().get(projectId)));
        projectView.selectTerminal(1);
        projectView.closeActiveTerminal();
        assertFalse(state.terminals().containsKey(projectTerminalId), ASSERTION_MESSAGE);

        state.updateCurrentTerminal(null);
        final var sessionView = GuiActionRunner.execute(() -> new SessionView(context));
        sessionView.selectTerminal(1);
        sessionView.closeActiveTerminal();
        assertFalse(state.terminals().containsKey(sessionTerminalId), ASSERTION_MESSAGE);

        projectView.dispose();
        sessionView.dispose();
    }

    @Test
    void sessionSummaryBuildsFallbackStatusPanels() {
        final var project = new Project(PROJECT_NAME, PROJECT_PATH, null);
        final var session =
                new Session(
                        null,
                        SESSION_NAME,
                        SESSION_AGENT,
                        "Investigate login",
                        "/path/does/not/exist");

        final var summary = GuiActionRunner.execute(() -> new SessionSummary(project, session));

        assertEquals(1, summary.getComponentCount(), "summary should render one details panel");
        final var details = (JPanel) summary.getComponent(0);
        assertTrue(
                componentText(details).contains("Investigate login"), "summary should show prompt");
        assertTrue(
                componentText(details).contains("/path/does/not/exist"),
                "summary should show path");
        assertTrue(summary.isVisible(), "assertion condition should hold");
    }

    private static String componentText(final java.awt.Component component) {
        if (component instanceof javax.swing.JLabel label) {
            return label.getText() == null ? "" : label.getText();
        }
        if (component instanceof javax.swing.text.JTextComponent text) {
            return text.getText();
        }
        if (component instanceof javax.swing.JComponent container) {
            final var text = new StringBuilder();
            for (final var child : container.getComponents()) {
                text.append(componentText(child)).append(' ');
            }
            return text.toString();
        }
        return "";
    }

    @Test
    void projectSettingsRendersAndSavesTheSelectedProject() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        state.updateCurrentProject(projectId);
        final var coordinator = new ViewCoordinator(state);
        final var context = new ActionContext(coordinator, state, null);

        final var view = GuiActionRunner.execute(() -> new ProjectSettingsView(context));
        final var rendered = (JPanel) view.getComponent(0);
        final var actions = (JPanel) rendered.getComponent(2);
        GuiActionRunner.execute((GuiActionRunnable) ((JButton) actions.getComponent(1))::doClick);

        assertEquals(ViewId.PROJECT, coordinator.currentViewId(), ASSERTION_MESSAGE);
        assertEquals(PROJECT_NAME, state.projects().get(projectId).name(), ASSERTION_MESSAGE);
        assertSame(view, view.render(), ASSERTION_MESSAGE);
    }

    @Test
    void projectSettingsRequiresASelectedProject() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var coordinator = new ViewCoordinator(state);

        assertThrows(
                RuntimeException.class,
                () ->
                        GuiActionRunner.execute(
                                () ->
                                        new ProjectSettingsView(
                                                new ActionContext(coordinator, state, null))));
    }

    @Test
    void sessionViewKeepsSummarySelectedForInvalidTerminalSelections()
            throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(
                                projectId,
                                SESSION_NAME,
                                SESSION_AGENT,
                                SESSION_PROMPT,
                                PROJECT_PATH));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        final var view =
                GuiActionRunner.execute(
                        () ->
                                new SessionView(
                                        new ActionContext(
                                                new ViewCoordinator(state), state, null)));
        final var tabs =
                (JTabbedPane) ((WorkspaceSplitPane) view.getComponent(1)).getLeftComponent();

        GuiActionRunner.execute(
                () -> {
                    view.selectTerminal(0);
                    view.selectTerminal(99);
                    view.openSummary();
                    view.render();
                });

        assertEquals(0, tabs.getSelectedIndex(), ASSERTION_MESSAGE);
        assertEquals(null, state.currentTerminalId(), ASSERTION_MESSAGE);
    }

    @Test
    void sessionViewRestoresPersistedAgentCommandAfterReopening()
            throws InvalidObjectException, InterruptedException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId =
                state.addProject(new Project(PROJECT_NAME, tempDirectory.toString(), null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(
                                projectId,
                                SESSION_NAME,
                                SESSION_AGENT,
                                SESSION_PROMPT,
                                tempDirectory.toString()));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        final var coordinator = new ViewCoordinator(state);
        final var context = new ActionContext(coordinator, state, null);

        final var initialView = GuiActionRunner.execute(() -> new SessionView(context));
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
                                                                    tempDirectory
                                                                            .resolve("restored")
                                                                            .toString())));
                            state.updateCurrentTerminal(id);
                            return id;
                        });
        assertEquals(1, state.sessions().get(sessionId).terminalIds().size(), ASSERTION_MESSAGE);
        assertTrue(
                java.nio.file.Files.isDirectory(
                        java.nio.file.Path.of(state.sessions().get(sessionId).worktreePath())),
                ASSERTION_MESSAGE);
        assertEquals(sessionId, state.currentSessionId(), ASSERTION_MESSAGE);
        assertEquals(terminalId, state.currentTerminalId(), ASSERTION_MESSAGE);
        final var createdView = GuiActionRunner.execute(() -> new SessionView(context));
        assertEquals(
                1,
                ((JTabbedPane)
                                ((WorkspaceSplitPane) createdView.getComponent(1))
                                        .getLeftComponent())
                        .getSelectedIndex(),
                ASSERTION_MESSAGE);
        com.jagent.desktop.test.AsyncTestSupport.await(
                () -> java.nio.file.Files.exists(tempDirectory.resolve("restored")),
                "restored terminal should execute its persisted command");
        GuiActionRunner.execute(() -> state.updateCurrentTerminal(null));
        final var reopenedView = GuiActionRunner.execute(() -> new SessionView(context));

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
    void projectViewRoutesPullRequestReviewsAndIgnoresInvalidTerminalSelections() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        final var coordinator = new ViewCoordinator(state);
        final var request =
                new PullRequest(
                        projectId, 1, "Title", "", "", "url", "", "", "", "", false, "", "", 0, 0,
                        "");
        final var view =
                GuiActionRunner.execute(
                        () ->
                                new ProjectView(
                                        new ActionContext(coordinator, state, null),
                                        state.projects().get(projectId)));
        view.reviewPullRequest(request);
        final var tabs =
                (JTabbedPane) ((WorkspaceSplitPane) view.getComponent(1)).getLeftComponent();
        GuiActionRunner.execute(
                () -> {
                    view.selectTerminal(0);
                    view.selectTerminal(99);
                    view.openSummary();
                    view.closeActiveTerminal();
                });

        assertEquals(0, tabs.getSelectedIndex(), ASSERTION_MESSAGE);
    }
}
