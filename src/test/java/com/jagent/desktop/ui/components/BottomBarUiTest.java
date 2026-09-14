package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.ui.Defaults;
import java.awt.Component;
import java.awt.Container;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.assertj.swing.edt.GuiActionRunnable;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class BottomBarUiTest {
    @Test
    void refreshButtonIsHiddenUntilEnabledAndInvokesCurrentViewCallback() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final AtomicInteger refreshes = new AtomicInteger();
        final var bottomBar =
                GuiActionRunner.execute(
                        () ->
                                new BottomBar(
                                        state,
                                        new BackgroundJobs(),
                                        () -> {},
                                        () -> {},
                                        () -> {},
                                        () -> {},
                                        refreshes::incrementAndGet));
        final JButton refreshButton = findButton(bottomBar, "refresh-button");

        assertNotNull(refreshButton, "refresh button should be present");
        assertFalse(refreshButton.isVisible(), "refresh button should start hidden");

        GuiActionRunner.execute(() -> bottomBar.setRefreshVisible(true));
        assertTrue(refreshButton.isVisible(), "refresh button should be visible when enabled");
        GuiActionRunner.execute((GuiActionRunnable) refreshButton::doClick);
        assertEquals(1, refreshes.get(), "refresh callback should be invoked once");

        GuiActionRunner.execute(() -> bottomBar.setRefreshVisible(false));
        assertFalse(refreshButton.isVisible(), "refresh button should hide when disabled");
    }

    @Test
    void reportsHowManyBackgroundJobsAreExecuting() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final BackgroundJobs jobs = new BackgroundJobs();
        final var bottomBar =
                GuiActionRunner.execute(
                        () ->
                                new BottomBar(
                                        state, jobs, () -> {}, () -> {}, () -> {}, () -> {},
                                        () -> {}));
        final JLabel status = findLabel(bottomBar, "jobs-status-label");

        assertNotNull(status, "job status label should be present");
        assertFalse(status.isVisible(), "job status label should be hidden without running jobs");

        final var firstJob = GuiActionRunner.execute(() -> jobs.start("First job"));

        assertTrue(status.isVisible(), "job status label should show while jobs execute");
        assertEquals("1 job running", status.getText(), "running job count should be shown");

        final var secondJob = GuiActionRunner.execute(() -> jobs.start("Second job"));

        assertEquals(
                "2 jobs running", status.getText(), "plural running job count should be shown");

        GuiActionRunner.execute(firstJob::complete);
        assertEquals(
                "1 job running", status.getText(), "count should decrease when a job completes");

        GuiActionRunner.execute(secondJob::complete);
        assertFalse(status.isVisible(), "job status label should hide when jobs complete");
    }

    @Test
    void jobMenuExplainsTheCurrentActivityAndOutcome() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final BackgroundJobs jobs = new BackgroundJobs();
        final var bottomBar =
                GuiActionRunner.execute(
                        () ->
                                new BottomBar(
                                        state, jobs, () -> {}, () -> {}, () -> {}, () -> {},
                                        () -> {}));
        final var runningJob =
                GuiActionRunner.execute(
                        () -> jobs.start("Session setup", "Demo project", "Fix login"));
        GuiActionRunner.execute(() -> runningJob.update("Running startup command 2 of 3"));
        GuiActionRunner.execute(() -> runningJob.output("Installing dependencies"));
        final var failedJob = GuiActionRunner.execute(() -> jobs.start("Worktree removal"));
        GuiActionRunner.execute(() -> failedJob.fail("Worktree contains changes"));

        final JPopupMenu menu = GuiActionRunner.execute(bottomBar::createJobsMenu);

        assertTrue(menu.getComponent(0) instanceof JLabel, "menu should have a heading");
        assertNotNull(
                findMenuItemContainingText(menu, "Session setup"),
                "session setup should be selectable");
        assertNotNull(
                findMenuItemContainingText(menu, "Worktree removal"),
                "worktree removal should be selectable");
    }

    private static JButton findButton(final Container container, final String name) {
        for (final Component component : container.getComponents()) {
            if (component instanceof JButton button && name.equals(button.getName())) {
                return button;
            }
            if (component instanceof Container child) {
                final JButton result = findButton(child, name);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static JLabel findLabel(final Container container, final String name) {
        for (final Component component : container.getComponents()) {
            if (component instanceof JLabel label && name.equals(label.getName())) {
                return label;
            }
            if (component instanceof Container child) {
                final JLabel result = findLabel(child, name);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static JMenuItem findMenuItemContainingText(
            final Container container, final String text) {
        for (final Component component : container.getComponents()) {
            if (component instanceof JMenuItem item && item.getText().contains(text)) {
                return item;
            }
            if (component instanceof Container child) {
                final JMenuItem result = findMenuItemContainingText(child, text);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
