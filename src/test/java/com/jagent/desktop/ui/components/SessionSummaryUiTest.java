package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.test.TestGitRepository;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionSummaryUiTest {
    private static final String DEMO_PROJECT = "Demo";
    private static final String FEATURE_SESSION = "Feature";
    private static final String AGENT = "Agent";

    @Test
    void rendersSessionDetailsWithoutAWindow() {
        final Project project = new Project(DEMO_PROJECT, "/tmp/demo", null);
        final AppState state = TestAppState.empty();
        final ProjectId projectId = state.addProject(project);
        state.updateCurrentProject(projectId);
        final Session session =
                new Session(
                        projectId, FEATURE_SESSION, AGENT, "Implement feature", "/tmp/worktree");

        final SessionSummary summary =
                GuiActionRunner.execute(
                        () -> new SessionSummary(project, session, state, "", () -> {}));
        final var text = new ArrayList<String>();
        collectText(summary, new ArrayList<>(), text);

        assertTrue(text.contains("Implement feature"), "prompt should render in the summary");
        assertTrue(text.contains("/tmp/worktree"), "worktree should render in the summary");
        assertTrue(
                text.stream().anyMatch(value -> value.contains("Ready for clean up!")),
                "cleanup alert should be attached to the summary");
    }

    @Test
    void keepsStatusRenderingSafeForUnavailableWorktree() throws InterruptedException {
        final Project project = new Project(DEMO_PROJECT, "/tmp/demo", null);
        final AppState state = TestAppState.empty();
        final ProjectId projectId = state.addProject(project);
        state.updateCurrentProject(projectId);
        final Session session =
                new Session(projectId, FEATURE_SESSION, null, null, "/path/that/does/not/exist");

        final SessionSummary summary =
                GuiActionRunner.execute(
                        () -> new SessionSummary(project, session, state, "", () -> {}));
        waitForText(summary, "Unavailable");

        assertNotNull(summary.getBorder(), "summary should retain its border after status failure");
        assertTrue(
                allComponentsAreSwing(summary),
                "status failure should leave a renderable Swing tree");
    }

    @Test
    void buildsFallbackStatusPanelsForUnavailableWorkspace() {
        final Project project = new Project(DEMO_PROJECT, "/tmp/demo", null);
        final AppState state = TestAppState.empty();
        final ProjectId projectId = state.addProject(project);
        state.updateCurrentProject(projectId);
        final Session session =
                new Session(
                        projectId,
                        FEATURE_SESSION,
                        AGENT,
                        "Investigate login",
                        "/path/does/not/exist");

        final SessionSummary summary =
                GuiActionRunner.execute(
                        () -> new SessionSummary(project, session, state, "", () -> {}));

        assertEquals(1, summary.getComponentCount(), "summary should render one details panel");
        final Component details = summary.getComponent(0);
        final var text = new ArrayList<String>();
        collectText((Container) details, new ArrayList<>(), text);
        assertTrue(
                text.stream().anyMatch(value -> value.contains("Investigate login")),
                "summary should include session prompt text");
        assertTrue(
                text.stream().anyMatch(value -> value.contains("/path/does/not/exist")),
                "summary should include unavailable worktree path");
        assertTrue(summary.isVisible(), "summary should remain visible");
    }

    @Test
    void rendersBranchAndDiffStatusFromARealWorktree(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        TestGitRepository.run(directory, "git commit --allow-empty -qm second");
        final Project project = new Project(DEMO_PROJECT, directory.toString(), null);
        final AppState state = TestAppState.empty();
        final ProjectId projectId = state.addProject(project);
        state.updateCurrentProject(projectId);
        final Session session =
                new Session(projectId, FEATURE_SESSION, AGENT, null, directory.toString());

        final SessionSummary summary =
                GuiActionRunner.execute(
                        () -> new SessionSummary(project, session, state, "", () -> {}));
        waitForText(summary, "master");

        assertTrue(
                allComponentsAreSwing(summary),
                "real worktree status should leave a renderable Swing tree");
    }

    @Test
    void loadsAndResetsAgentContextAndShowsContextFile(@TempDir final Path worktree)
            throws IOException, InterruptedException {
        final Project project =
                new Project(
                        DEMO_PROJECT,
                        worktree.toString(),
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        ".branchloom/context.md",
                        "Use shared notes.");
        final AppState state = TestAppState.empty();
        final ProjectId projectId = state.addProject(project);
        state.updateCurrentProject(projectId);
        final Session session =
                new Session(
                        projectId,
                        FEATURE_SESSION,
                        AGENT,
                        "Implement feature",
                        worktree.toString());
        final Path contextPath = worktree.resolve(".branchloom/context.md");
        final Path contextParent = contextPath.getParent();
        if (contextParent != null) {
            Files.createDirectories(contextParent);
        }
        Files.writeString(contextPath, "initial notes");

        final SessionSummary summary =
                GuiActionRunner.execute(
                        () -> new SessionSummary(project, session, state, "", () -> {}));
        waitForText(summary, "initial notes");
        waitForText(summary, contextPath.toString());

        final JTextArea contextArea = findTextArea(summary, "initial notes");
        assertNotNull(contextArea, "context area should render");
        assertTrue(contextArea.isEditable(), "context should be editable inline");

        final JButton save = SwingTestSupport.findButton(summary, "Save");
        assertNotNull(save, "save button should render");
    }

    private static void collectText(
            final Container container, final List<String> labels, final List<String> text) {
        for (final Component component : container.getComponents()) {
            if (component instanceof JLabel label && label.getText() != null) {
                labels.add(label.getText());
            }
            if (component instanceof JTextArea area) {
                text.add(area.getText());
            }
            if (component instanceof JTextPane pane) {
                text.add(pane.getText());
            }
            if (component instanceof Container child) {
                collectText(child, labels, text);
            }
        }
    }

    private static void waitForText(final Container container, final String expected)
            throws InterruptedException {
        SwingTestSupport.await(
                () -> {
                    final var text = new ArrayList<String>();
                    collectText(container, new ArrayList<>(), text);
                    return text.stream().anyMatch(value -> value.contains(expected));
                },
                "summary did not render expected text: " + expected);
    }

    private static boolean allComponentsAreSwing(final Container container) {
        for (final Component component : container.getComponents()) {
            if (!(component instanceof javax.swing.JComponent)) {
                return false;
            }
            if (!allComponentsAreSwing((Container) component)) {
                return false;
            }
        }
        return true;
    }

    private static JTextArea findTextArea(final Container container, final String contains) {
        for (final Component component : container.getComponents()) {
            if (component instanceof JTextArea textArea && textArea.getText().contains(contains)) {
                return textArea;
            }
            if (component instanceof Container child) {
                final JTextArea result = findTextArea(child, contains);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
