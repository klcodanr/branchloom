package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jagent.desktop.api.Action;
import org.junit.jupiter.api.Test;

class UiIconsTest {
    @Test
    void resolvesKnownAndTerminalActionIcons() {
        assertNotNull(UiIcons.forAction(action("open-project")), "known actions should have icons");
        assertNotNull(
                UiIcons.forAction(action("new-terminal-1")),
                "new terminal actions should use the terminal icon");
    }

    @Test
    void returnsNoIconForUnknownAction() {
        assertNull(UiIcons.forAction(action("unknown")), "unknown actions should have no icon");
    }

    private static Action action(final String id) {
        return new Action() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String label() {
                return id;
            }

            @Override
            public void execute() {}
        };
    }
}
