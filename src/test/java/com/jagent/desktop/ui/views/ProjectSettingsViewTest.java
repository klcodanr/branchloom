package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.*;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.github.Credential;
import com.jagent.desktop.models.github.PatCredential;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import org.assertj.swing.edt.GuiActionRunnable;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class ProjectSettingsViewTest {
    private static final String PROJECT_NAME = "Demo";
    private static final String PROJECT_PATH = "/tmp/demo";
    private static final String PROJECT_NAME_UPDATED = "Demo Updated";
    private static final String PROJECT_NAME_RENAMED = "Another Name";
    private static final String GROUP_ORIGINAL = "group-a";
    private static final String GROUP_UPDATED = "group-b";
    private static final String TEMPLATE_ORIGINAL = "{projectName}";
    private static final String TEMPLATE_UPDATED = "{projectPath}";
    private static final String STARTUP_ORIGINAL = "echo one";
    private static final String STARTUP_UPDATED = "echo one\necho two";
    private static final String CONTEXT_PATH_ORIGINAL = ".agent.md";
    private static final String CONTEXT_PATH_UPDATED = ".session-context.md";
    private static final String CONTEXT_TEXT_ORIGINAL = "ctx";
    private static final String CONTEXT_TEXT_UPDATED = "new ctx";
    private static final String CONNECTION_ID = "conn-1";
    private static final String CONNECTION_NAME = "Personal token";
    private static final String HOST = "github.com";

    @Test
    void rendersAndSavesTheSelectedProject() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ProjectId projectId = state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        state.updateCurrentProject(projectId);
        final ViewCoordinator coordinator = new ViewCoordinator(state);
        final ActionContext context = new ActionContext(coordinator, state, null);

        final ProjectSettingsView view =
                GuiActionRunner.execute(() -> new ProjectSettingsView(context));
        final JPanel rendered = (JPanel) view.getComponent(0);
        final JPanel actions = (JPanel) rendered.getComponent(2);
        GuiActionRunner.execute((GuiActionRunnable) ((JButton) actions.getComponent(1))::doClick);

        assertEquals(ViewId.PROJECT, coordinator.currentViewId(), "save should return to project");
        assertEquals(PROJECT_NAME, state.projects().get(projectId).name(), "name should persist");
        assertSame(view, view.render(), "render should return view instance");
    }

    @Test
    void requiresASelectedProjectForConstruction() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ViewCoordinator coordinator = new ViewCoordinator(state);

        assertThrows(
                RuntimeException.class,
                () ->
                        GuiActionRunner.execute(
                                () ->
                                        new ProjectSettingsView(
                                                new ActionContext(coordinator, state, null))));
    }

    @Test
    void createBuildsSettingsFormForSelectedProject() {
        final Project project = new Project(PROJECT_NAME, PROJECT_PATH, null);
        final ProjectId projectId = ProjectId.create();
        final AppState state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), project),
                        Map.of(),
                        Map.of());
        state.updateCurrentProject(projectId);

        final JComponent view =
                ProjectSettingsView.create(
                        new ActionContext(new ViewCoordinator(state), state, null));

        assertNotNull(view, "settings view should render for selected project");
        assertTrue(view.getComponentCount() > 0, "settings view should include content");
    }

    @Test
    void saveProjectAndChangeDetectionWorkForUpdatedValues() {
        final Project project =
                new Project(
                        PROJECT_NAME,
                        PROJECT_PATH,
                        GROUP_ORIGINAL,
                        new PatCredential(CONNECTION_ID, HOST, CONNECTION_NAME),
                        TEMPLATE_ORIGINAL,
                        null,
                        List.of(STARTUP_ORIGINAL),
                        List.of(),
                        CONTEXT_PATH_ORIGINAL,
                        CONTEXT_TEXT_ORIGINAL);
        final ProjectId projectId = ProjectId.create();
        final AppState state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), project),
                        Map.of(),
                        Map.of(),
                        Map.of(
                                CONNECTION_ID,
                                new PatCredential(CONNECTION_ID, HOST, CONNECTION_NAME)));

        final JTextField name = new JTextField(PROJECT_NAME_UPDATED);
        final JTextField group = new JTextField(GROUP_UPDATED);
        final JTextField template = new JTextField(TEMPLATE_UPDATED);
        final JTextArea startup = new JTextArea(STARTUP_UPDATED);
        final JTextField contextPath = new JTextField(CONTEXT_PATH_UPDATED);
        final JTextArea contextText = new JTextArea(CONTEXT_TEXT_UPDATED);
        final JComboBox<Credential> auths =
                new JComboBox<>(
                        new Credential[] {new PatCredential(CONNECTION_ID, HOST, CONNECTION_NAME)});
        final AtomicBoolean closed = new AtomicBoolean();

        ProjectSettingsView.saveProject(
                state,
                project,
                name,
                group,
                template,
                startup,
                contextPath,
                contextText,
                auths,
                () -> closed.set(true));

        final Project updated = state.projects().get(projectId);
        assertEquals(PROJECT_NAME_UPDATED, updated.name(), "save should update project name");
        assertEquals(GROUP_UPDATED, updated.group(), "save should update project group");
        assertEquals(TEMPLATE_UPDATED, updated.worktreeTemplate(), "save should update template");
        assertEquals(
                List.of("echo one", "echo two"),
                updated.startupCommands(),
                "save should parse startup lines");
        assertEquals(
                CONTEXT_PATH_UPDATED,
                updated.agentContextPath(),
                "save should update context path");
        assertEquals(
                CONTEXT_TEXT_UPDATED,
                updated.agentContextText(),
                "save should update context text");
        assertTrue(closed.get(), "save should invoke close callback after updating state");

        final boolean unchanged =
                ProjectSettingsView.hasChanges(
                        PROJECT_NAME_UPDATED,
                        GROUP_UPDATED,
                        TEMPLATE_UPDATED,
                        STARTUP_UPDATED,
                        CONTEXT_PATH_UPDATED,
                        CONTEXT_TEXT_UPDATED,
                        CONNECTION_ID,
                        name,
                        group,
                        template,
                        startup,
                        contextPath,
                        contextText,
                        auths);
        assertFalse(unchanged, "matching values should not be reported as dirty");

        name.setText(PROJECT_NAME_RENAMED);
        final boolean changed =
                ProjectSettingsView.hasChanges(
                        PROJECT_NAME_UPDATED,
                        GROUP_UPDATED,
                        TEMPLATE_UPDATED,
                        STARTUP_UPDATED,
                        CONTEXT_PATH_UPDATED,
                        CONTEXT_TEXT_UPDATED,
                        CONNECTION_ID,
                        name,
                        group,
                        template,
                        startup,
                        contextPath,
                        contextText,
                        auths);
        assertTrue(changed, "changed name should mark settings as dirty");
    }
}
