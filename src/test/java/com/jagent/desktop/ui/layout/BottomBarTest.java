package com.jagent.desktop.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.ui.Defaults;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class BottomBarTest {
    @Test
    void createJobsMenuShowsPlaceholderWithoutJobs() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final BackgroundJobs jobs = new BackgroundJobs();
        final BottomBar bar =
                GuiActionRunner.execute(
                        () ->
                                new BottomBar(
                                        state, jobs, () -> {}, () -> {}, () -> {}, () -> {},
                                        () -> {}));

        final JPopupMenu menu = GuiActionRunner.execute(bar::createJobsMenu);

        assertEquals(1, menu.getComponentCount(), "menu should only show the empty placeholder");
        assertTrue(menu.getComponent(0) instanceof JMenuItem, "placeholder should be a menu item");
        final JMenuItem placeholder = (JMenuItem) menu.getComponent(0);
        assertEquals("No background jobs", placeholder.getText(), "empty message should match");
        assertFalse(placeholder.isEnabled(), "placeholder should be disabled");
    }

    @Test
    void createJobsMenuRendersSortedJobsAndStatuses() throws InterruptedException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final BackgroundJobs jobs = new BackgroundJobs();
        final BottomBar bar =
                GuiActionRunner.execute(
                        () ->
                                new BottomBar(
                                        state, jobs, () -> {}, () -> {}, () -> {}, () -> {},
                                        () -> {}));

        final BackgroundJobs.Handle build = jobs.start("Build", "Demo", "Feature");
        build.fail("Build failed");
        final BackgroundJobs.Handle analyze = jobs.start("Analyze", "", "");
        analyze.update("Analyzing");

        SwingTestSupport.await(
                () -> {
                    final JLabel status = findLabel(bar, "jobs-status-label");
                    return status != null && status.isVisible();
                },
                "jobs status label did not update");

        final JPopupMenu menu = GuiActionRunner.execute(bar::createJobsMenu);
        final List<String> items =
                java.util.Arrays.stream(menu.getComponents())
                        .filter(JMenuItem.class::isInstance)
                        .map(component -> ((JMenuItem) component).getText())
                        .toList();
        assertEquals(
                List.of("Analyze  ·  Running", "Build  ·  Failed"),
                items,
                "jobs should be sorted by title and include status text");
    }

    @Test
    void refreshShowsCurrentProjectName() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project("Demo", "/tmp/demo", null));
        state.updateCurrentProject(projectId);
        final BottomBar bar =
                GuiActionRunner.execute(
                        () ->
                                new BottomBar(
                                        state,
                                        new BackgroundJobs(),
                                        () -> {},
                                        () -> {},
                                        () -> {},
                                        () -> {},
                                        () -> {}));

        final JLabel projectLabel = findLabelWithText(bar, "Demo");
        assertNotNull(projectLabel, "project name should be shown after refresh");
    }

    private static JLabel findLabel(final Container container, final String name) {
        for (final Component component : container.getComponents()) {
            if (component instanceof JLabel label && name.equals(label.getName())) {
                return label;
            }
            if (component instanceof Container child) {
                final JLabel found = findLabel(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JLabel findLabelWithText(final Container container, final String text) {
        for (final Component component : container.getComponents()) {
            if (component instanceof JLabel label && text.equals(label.getText())) {
                return label;
            }
            if (component instanceof Container child) {
                final JLabel found = findLabelWithText(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
