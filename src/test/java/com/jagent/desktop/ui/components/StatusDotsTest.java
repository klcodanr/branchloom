package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jagent.desktop.services.terminal.TerminalState;
import java.awt.Color;
import javax.swing.Icon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StatusDotsTest {
    @BeforeAll
    static void configureTheme() {
        Theme.applySwingDefaults();
    }

    @Test
    void updatesColorAndTooltip() {
        final StatusDot dot = new StatusDot(Color.BLUE, "initial");

        dot.update(Color.RED, "updated");

        assertEquals("updated", dot.getToolTipText(), "updated tooltip should be displayed");
        assertEquals(10, dot.getPreferredSize().width, "dot width should remain standard");
    }

    @Test
    void terminalIconsHaveStandardDimensionsForEveryState() {
        for (final TerminalState state : TerminalState.values()) {
            final Icon icon = StatusDot.terminalIcon(state);

            assertNotNull(icon, "each state should have an icon");
            assertEquals(10, icon.getIconWidth(), "icon width should be standard");
            assertEquals(10, icon.getIconHeight(), "icon height should be standard");
        }
    }
}
