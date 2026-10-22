package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.Defaults;
import com.jagent.desktop.ui.layout.BottomBar;
import java.awt.Component;
import java.awt.Container;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JLabel;
import org.assertj.swing.edt.GuiActionRunnable;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class BottomBarUiTest {
    @Test
    void refreshButtonIsHiddenUntilEnabledAndInvokesCurrentViewCallback() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var controller = TestAppState.controller(state);
        final var bottomBar =
                GuiActionRunner.execute(
                        () ->
                                new BottomBar(
                                        new com.jagent.desktop.models.ActionContext(
                                                controller, state, null, null, () -> {})));
        final JButton refreshButton = findButton(bottomBar, "refresh-button");
        assertNotNull(refreshButton, "bottom bar should include refresh button");
        assertFalse(refreshButton.isVisible(), "refresh button should start hidden");

        controller.updateView(
                com.jagent.desktop.api.ViewId.BOARD,
                com.jagent.desktop.services.ViewController.ViewState.board(
                        state.appSettings().boards().getFirst().name()));
        bottomBar.updateView(controller.currentView());
        assertTrue(
                refreshButton.isVisible(),
                "refresh button should become visible for refreshable views");
        GuiActionRunner.execute((GuiActionRunnable) refreshButton::doClick);
    }

    @Test
    void reportsHowManyBackgroundJobsAreExecuting() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var controller = TestAppState.controller(state);
        final BackgroundJobs jobs = controller.backgroundJobs();
        final var bottomBar =
                GuiActionRunner.execute(
                        () ->
                                new BottomBar(
                                        new com.jagent.desktop.models.ActionContext(
                                                controller, state, null, null, () -> {})));
        final JLabel status = findLabel(bottomBar, "jobs-status-label");
        assertNotNull(status, "bottom bar should include jobs status label");
        assertFalse(status.isVisible(), "jobs status should start hidden");

        final var handle = GuiActionRunner.execute(() -> jobs.start("job"));
        assertTrue(status.isVisible(), "jobs status should be visible while a job is running");
        GuiActionRunner.execute(handle::complete);
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
}
