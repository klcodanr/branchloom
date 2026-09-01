package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.AppSettings;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Tool;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import com.jagent.desktop.ui.components.SessionSummary;
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

class TargetViewsUiTest {
    private static final String ASSERTION_MESSAGE = "target view behavior should match";
    private static final String PROJECT_NAME = "Demo";
    private static final String PROJECT_PATH = "/tmp";
    private static final String SESSION_NAME = "Feature";

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
        final var scroll = (JScrollPane) rendered.getComponent(1);
        final var body = (JPanel) scroll.getViewport().getView();
        final var tabs = (JTabbedPane) body.getComponent(0);
        final var agents = (JPanel) tabs.getComponentAt(1);
        final var editors = (JPanel) tabs.getComponentAt(2);
        final JButton addAgent = (JButton) agents.getComponent(2);
        final JButton addEditor = (JButton) editors.getComponent(2);
        GuiActionRunner.execute(
                () -> {
                    addAgent.doClick();
                    addEditor.doClick();
                });

        final var actions = (JPanel) rendered.getComponent(2);
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
        final var actions = (JPanel) rendered.getComponent(2);

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
        assertEquals(3, ((JTabbedPane) view.getComponent(1)).getTabCount(), ASSERTION_MESSAGE);
        assertSame(view, view.render(), ASSERTION_MESSAGE);
        assertTrue(!view.focusPullRequestSearch(), ASSERTION_MESSAGE);
        view.openSummary();
        view.selectTerminal(0);
        view.closeActiveTerminal();
        view.renameActiveTerminal();
        view.dispose();
    }

    @Test
    void sessionViewBuildsSummaryAndHandlesNoTerminalSelection() throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(projectId, SESSION_NAME, "agent", "prompt", PROJECT_PATH));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        final var coordinator = new ViewCoordinator(state);
        final var context = new ActionContext(coordinator, state, null);

        final var view = GuiActionRunner.execute(() -> new SessionView(context));

        assertEquals(ViewId.SESSION, view.id(), ASSERTION_MESSAGE);
        assertEquals(SESSION_NAME, view.title(), ASSERTION_MESSAGE);
        assertEquals(2, ((JTabbedPane) view.getComponent(1)).getTabCount(), ASSERTION_MESSAGE);
        assertSame(view, view.render(), ASSERTION_MESSAGE);
        view.selectTerminal(0);
        view.openSummary();
        view.closeActiveTerminal();
        view.renameActiveTerminal();
        view.dispose();
    }

    @Test
    void sessionSummaryBuildsFallbackStatusPanels() {
        final var project = new Project(PROJECT_NAME, PROJECT_PATH, null);
        final var session =
                new Session(
                        null, SESSION_NAME, "agent", "Investigate login", "/path/does/not/exist");

        final var summary = GuiActionRunner.execute(() -> new SessionSummary(project, session));

        assertEquals(2, summary.getComponentCount(), "assertion values should match");
        final var details = (JPanel) summary.getComponent(1);
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
                        new Session(projectId, SESSION_NAME, "agent", "prompt", PROJECT_PATH));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        final var view =
                GuiActionRunner.execute(
                        () ->
                                new SessionView(
                                        new ActionContext(
                                                new ViewCoordinator(state), state, null)));
        final var tabs = (JTabbedPane) view.getComponent(1);

        GuiActionRunner.execute(
                () -> {
                    view.selectTerminal(0);
                    view.selectTerminal(99);
                    view.openSummary();
                    view.render();
                });

        assertEquals(1, tabs.getSelectedIndex(), ASSERTION_MESSAGE);
        assertEquals(null, state.currentTerminalId(), ASSERTION_MESSAGE);
    }

    @Test
    void projectViewRoutesPullRequestReviewsAndIgnoresInvalidTerminalSelections() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        final var coordinator = new ViewCoordinator(state);
        final var view =
                GuiActionRunner.execute(
                        () ->
                                new ProjectView(
                                        new ActionContext(coordinator, state, null),
                                        state.projects().get(projectId)));
        final var tabs = (JTabbedPane) view.getComponent(1);
        final var request =
                new com.jagent.desktop.models.PullRequest(
                        projectId, 1, "Title", "", "", "url", "", "", "", "", false, "", "", 0, 0,
                        "");

        GuiActionRunner.execute(
                () -> {
                    view.reviewPullRequest(request);
                    view.selectTerminal(0);
                    view.selectTerminal(99);
                    view.openSummary();
                    view.closeActiveTerminal();
                });

        assertEquals(0, tabs.getSelectedIndex(), ASSERTION_MESSAGE);
    }
}
