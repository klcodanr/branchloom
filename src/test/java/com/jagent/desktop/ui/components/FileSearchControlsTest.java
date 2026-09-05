package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JLabel;
import org.assertj.swing.edt.GuiActionRunnable;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class FileSearchControlsTest {
    @Test
    void findsCaseInsensitiveNonOverlappingMatches() {
        assertEquals(
                List.of(0, 6),
                FileSearchControls.findMatches("Alpha alpha", "ALPHA"),
                "matches should be case insensitive and non-overlapping");
    }

    @Test
    void navigatesMatchesAndUpdatesCount() throws InterruptedException {
        final AtomicReference<String> selected = new AtomicReference<>();
        final FileSearchControls controls =
                GuiActionRunner.execute(
                        () ->
                                new FileSearchControls(
                                        () -> "Alpha\nalpha\nbeta",
                                        (start, end) -> selected.set(start + ":" + end),
                                        () -> {},
                                        () -> {}));
        final JButton searchButton = button(controls, "file-search-button");
        final SearchInput searchInput = (SearchInput) component(controls, "file-search");
        final JLabel count = (JLabel) component(controls, "file-search-count");
        final JButton next = button(controls, "file-search-next");

        GuiActionRunner.execute((GuiActionRunnable) searchButton::doClick);
        GuiActionRunner.execute(() -> searchInput.setText("ALPHA"));
        waitForCount(count, "(1/2)");
        assertEquals("0:5", selected.get(), "first match should be selected");
        GuiActionRunner.execute((GuiActionRunnable) next::doClick);
        assertEquals("(2/2)", count.getText(), "count should advance after next");
        assertEquals("6:11", selected.get(), "second match should be selected");
        assertTrue(next.isEnabled(), "next should be enabled when matches exist");

        GuiActionRunner.execute(() -> searchInput.setText("missing"));
        waitForCount(count, "(0/0)");
        assertFalse(next.isEnabled(), "next should be disabled when there are no matches");
    }

    private static Component component(final Container parent, final String name) {
        if (name.equals(parent.getName())) {
            return parent;
        }
        for (final Component child : parent.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof Container container) {
                final Component result = component(container, name);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static JButton button(final Container parent, final String name) {
        return (JButton) component(parent, name);
    }

    private static void waitForCount(final JLabel count, final String expected)
            throws InterruptedException {
        final long deadline = System.nanoTime() + 3_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (GuiActionRunner.execute(() -> expected.equals(count.getText()))) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("search count did not render: " + expected);
    }
}
