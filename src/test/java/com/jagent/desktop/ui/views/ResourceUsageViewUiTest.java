package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jagent.desktop.services.TerminalResources;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTable;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class ResourceUsageViewUiTest {
    private static final String TERMINAL_COUNT = "terminalCount";
    private static final String TERMINAL_CPU = "terminalCpu";
    private static final String TERMINAL_MEMORY = "terminalMemory";
    private static final String TERMINAL_MODEL = "terminalModel";
    private static final String ASSERTION_MESSAGE = "resource usage view should match";

    @Test
    void rendersEmptyTerminalSample() {
        final var view = GuiActionRunner.execute(() -> new ResourceUsageView(false));

        update(view, new TerminalResources.Sample(List.of(), false));

        assertEquals("0", label(view, TERMINAL_COUNT).getText(), ASSERTION_MESSAGE);
        assertEquals("0m 00s", label(view, TERMINAL_CPU).getText(), ASSERTION_MESSAGE);
        assertEquals("Unavailable", label(view, TERMINAL_MEMORY).getText(), ASSERTION_MESSAGE);
        assertEquals(
                "No active terminals",
                table(view, TERMINAL_MODEL).getValueAt(0, 0),
                ASSERTION_MESSAGE);
    }

    @Test
    void rendersTerminalUsageWhenMemoryIsAvailable() {
        final var view = GuiActionRunner.execute(() -> new ResourceUsageView(false));
        final var usage = new TerminalResources.Usage("agent", 42, 2, 61_000, 2 * 1024 * 1024L);

        update(view, new TerminalResources.Sample(List.of(usage), true));

        assertEquals("1", label(view, TERMINAL_COUNT).getText(), ASSERTION_MESSAGE);
        assertEquals("1m 01s", label(view, TERMINAL_CPU).getText(), ASSERTION_MESSAGE);
        assertEquals("2.0 MB", label(view, TERMINAL_MEMORY).getText(), ASSERTION_MESSAGE);
        assertEquals("agent", table(view, TERMINAL_MODEL).getValueAt(0, 0), ASSERTION_MESSAGE);
        assertEquals("2.0 MB", table(view, TERMINAL_MODEL).getValueAt(0, 4), ASSERTION_MESSAGE);
    }

    @Test
    void rendersUnavailableMemoryAndKilobyteFormatting() {
        final var view = GuiActionRunner.execute(() -> new ResourceUsageView(false));
        final var usage = new TerminalResources.Usage("agent", 7, 1, 1_000, 512 * 1024L);

        update(view, new TerminalResources.Sample(List.of(usage), true));

        assertEquals("1", label(view, TERMINAL_COUNT).getText(), ASSERTION_MESSAGE);
        assertEquals("0m 01s", label(view, TERMINAL_CPU).getText(), ASSERTION_MESSAGE);
        assertEquals("512 KB", label(view, TERMINAL_MEMORY).getText(), ASSERTION_MESSAGE);
        assertEquals("512 KB", table(view, TERMINAL_MODEL).getValueAt(0, 4), ASSERTION_MESSAGE);

        update(view, new TerminalResources.Sample(List.of(usage), false));

        assertEquals("Unavailable", label(view, TERMINAL_MEMORY).getText(), ASSERTION_MESSAGE);
        assertEquals(
                "Unavailable", table(view, TERMINAL_MODEL).getValueAt(0, 4), ASSERTION_MESSAGE);
    }

    private static void update(
            final ResourceUsageView view, final TerminalResources.Sample sample) {
        GuiActionRunner.execute(() -> view.updateTerminals(sample));
        GuiActionRunner.execute(() -> {});
    }

    private static JLabel label(final ResourceUsageView view, final String name) {
        final String title =
                switch (name) {
                    case TERMINAL_COUNT -> "Active terminals";
                    case TERMINAL_CPU -> "CPU time";
                    case TERMINAL_MEMORY -> "Resident memory";
                    default -> throw new IllegalArgumentException("unknown metric: " + name);
                };
        final JLabel titleLabel = findLabel(view, title);
        assertNotNull(titleLabel, "metric title should be present: " + title);
        final Container metric = titleLabel.getParent();
        return (JLabel) metric.getComponent(1);
    }

    private static JTable table(final ResourceUsageView view, final String name) {
        final JTable result = findTable(view, TERMINAL_MODEL.equals(name) ? "Terminal" : name);
        assertNotNull(result, "table should be present: " + name);
        return result;
    }

    private static JLabel findLabel(final Container container, final String text) {
        for (final Component component : container.getComponents()) {
            if (component instanceof JLabel label && text.equals(label.getText())) {
                return label;
            }
            if (component instanceof Container child) {
                final JLabel match = findLabel(child, text);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static JTable findTable(final Container container, final String firstColumn) {
        for (final Component component : container.getComponents()) {
            if (component instanceof JTable table && firstColumn.equals(table.getColumnName(0))) {
                return table;
            }
            if (component instanceof Container child) {
                final JTable match = findTable(child, firstColumn);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
