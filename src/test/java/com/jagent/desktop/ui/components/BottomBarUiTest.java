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
}
