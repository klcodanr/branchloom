package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.*;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import com.jagent.desktop.ui.layout.WorkspaceSplitPane;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Map;
import javax.swing.JTabbedPane;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectViewUiTest {
    private static final String ASSERTION_MESSAGE = "target view behavior should match";
    private static final String PROJECT_NAME = "Demo";
    private static final String PROJECT_PATH = "/tmp";
    private static final String SESSION_NAME = "Feature";
    private static final String SESSION_PROMPT = "prompt";
    private static final String SUCCESS_COMMAND = "true";

    @Test
    void buildsTabsAndSupportsViewOperations() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        final ActionContext context = new ActionContext(new ViewCoordinator(state), state, null);
        final Project project = state.projects().get(projectId);

        final ProjectView view = GuiActionRunner.execute(() -> new ProjectView(context, project));

        assertEquals(ViewId.PROJECT, view.id(), ASSERTION_MESSAGE);
        assertEquals(PROJECT_NAME, view.title(), ASSERTION_MESSAGE);
        final WorkspaceSplitPane split = (WorkspaceSplitPane) view.getComponent(1);
        assertEquals(1, ((JTabbedPane) split.getLeftComponent()).getTabCount(), ASSERTION_MESSAGE);
        assertNotNull(split.getRightComponent(), ASSERTION_MESSAGE);
        assertSame(view, view.render(), ASSERTION_MESSAGE);
        view.openSummary();
        view.selectTerminal(0);
        view.closeActiveTerminal();
        view.renameActiveTerminal();
        view.dispose();
    }

    @Test
    void closingActiveFileSelectsTheNextAvailableTabAndDoesNothingWithoutAFile(
            @TempDir final Path directory) throws IOException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId =
                state.addProject(new Project(PROJECT_NAME, directory.toString(), null));
        final ActionContext context = new ActionContext(new ViewCoordinator(state), state, null);
        final var firstFile =
                java.nio.file.Files.writeString(directory.resolve("first.txt"), "first");
        final var secondFile =
                java.nio.file.Files.writeString(directory.resolve("second.txt"), "second");
        final ProjectView view =
                GuiActionRunner.execute(
                        () -> new ProjectView(context, state.projects().get(projectId)));
        final JTabbedPane tabs =
                (JTabbedPane) ((WorkspaceSplitPane) view.getComponent(1)).getLeftComponent();

        GuiActionRunner.execute(
                () -> {
                    view.openFile(firstFile);
                    view.openFile(secondFile);
                });

        assertEquals(3, tabs.getTabCount(), ASSERTION_MESSAGE);
        assertEquals("second.txt", tabs.getTitleAt(tabs.getSelectedIndex()), ASSERTION_MESSAGE);
        assertTrue(view.closeActiveFile(), ASSERTION_MESSAGE);
        assertEquals(2, tabs.getTabCount(), ASSERTION_MESSAGE);
        assertEquals("first.txt", tabs.getTitleAt(tabs.getSelectedIndex()), ASSERTION_MESSAGE);
        assertTrue(view.closeActiveFile(), ASSERTION_MESSAGE);
        assertEquals(1, tabs.getTabCount(), ASSERTION_MESSAGE);
        assertFalse(view.closeActiveFile(), ASSERTION_MESSAGE);
        assertEquals(1, tabs.getTabCount(), ASSERTION_MESSAGE);
        view.dispose();
    }

    @Test
    void doesNotRenderSessionTerminals() throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(
                                projectId, SESSION_NAME, "agent", SESSION_PROMPT, PROJECT_PATH));
        state.addTerminal(sessionId, new Terminal(sessionId, "Shell", SUCCESS_COMMAND));
        state.addTerminal(
                sessionId, new Terminal(sessionId, projectId, "Dual owner", SUCCESS_COMMAND));
        final ActionContext context = new ActionContext(new ViewCoordinator(state), state, null);

        final ProjectView view =
                GuiActionRunner.execute(
                        () -> new ProjectView(context, state.projects().get(projectId)));
        GuiActionRunner.execute(() -> {});

        assertEquals(
                1,
                ((JTabbedPane) ((WorkspaceSplitPane) view.getComponent(1)).getLeftComponent())
                        .getTabCount(),
                "session terminals should not appear in project tabs");
        view.dispose();
    }

    @Test
    void ignoresInvalidTerminalSelections() throws MalformedURLException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        final ProjectView view =
                GuiActionRunner.execute(
                        () ->
                                new ProjectView(
                                        new ActionContext(new ViewCoordinator(state), state, null),
                                        state.projects().get(projectId)));
        final JTabbedPane tabs =
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
