package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.test.TestGitRepository;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionSummaryUiTest {
    @Test
    void rendersSessionDetailsWithoutAWindow() {
        final Project project = new Project("Demo", "/tmp/demo", null);
        final Session session =
                new Session(null, "Feature", "Agent", "Implement feature", "/tmp/worktree");

        final var summary = GuiActionRunner.execute(() -> new SessionSummary(project, session));
        final var text = new ArrayList<String>();
        collectText(summary, new ArrayList<>(), text);

        assertTrue(text.contains("Implement feature"), "prompt should render in the summary");
        assertTrue(text.contains("/tmp/worktree"), "worktree should render in the summary");
        assertTrue(
                text.stream().anyMatch(value -> value.contains("Ready for clean up!")),
                "cleanup alert should be attached to the summary");
        assertTrue(
                text.stream().anyMatch(value -> value.contains("Loading")),
                "status values should start loading");
    }

    @Test
    void keepsStatusRenderingSafeForUnavailableWorktree() throws InterruptedException {
        final Project project = new Project("Demo", "/tmp/demo", null);
        final Session session =
                new Session(null, "Feature", null, null, "/path/that/does/not/exist");

        final var summary = GuiActionRunner.execute(() -> new SessionSummary(project, session));
        waitForText(summary, "Unavailable");

        assertNotNull(summary.getBorder(), "summary should retain its border after status failure");
        assertTrue(
                allComponentsAreSwing(summary),
                "status failure should leave a renderable Swing tree");
    }

    @Test
    void rendersBranchAndDiffStatusFromARealWorktree(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        TestGitRepository.run(directory, "git commit --allow-empty -qm second");
        final Project project = new Project("Demo", directory.toString(), null);
        final Session session = new Session(null, "Feature", "Agent", null, directory.toString());

        final var summary = GuiActionRunner.execute(() -> new SessionSummary(project, session));
        waitForText(summary, "master");
        waitForText(summary, "No changes in worktree");

        assertTrue(
                allComponentsAreSwing(summary),
                "real worktree status should leave a renderable Swing tree");
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
        final long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            final var text = new ArrayList<String>();
            collectText(container, new ArrayList<>(), text);
            if (text.stream().anyMatch(value -> value.contains(expected))) {
                return;
            }
            GuiActionRunner.execute(() -> {});
            Thread.sleep(10);
        }
        throw new AssertionError("summary did not render expected text: " + expected);
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
}
