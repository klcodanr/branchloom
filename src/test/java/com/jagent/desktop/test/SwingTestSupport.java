package com.jagent.desktop.test;

import static org.junit.jupiter.api.Assertions.fail;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

/** Shared Swing component and EDT helpers for headless UI tests. */
public final class SwingTestSupport {
    private SwingTestSupport() {}

    public static void flushEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            try {
                SwingUtilities.invokeAndWait(() -> {});
            } catch (Exception exception) {
                fail("EDT did not flush", exception);
            }
        }
    }

    public static <T extends Component> T find(final Container root, final Class<T> type) {
        return find(root, type, ignored -> true);
    }

    public static <T extends Component> T find(
            final Container root, final Class<T> type, final Predicate<T> predicate) {
        for (final Component child : root.getComponents()) {
            if (type.isInstance(child) && predicate.test(type.cast(child))) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                final T found = find(container, type, predicate);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public static List<JButton> buttons(final Container root) {
        final List<JButton> buttons = new ArrayList<>();
        collectButtons(root, buttons);
        return buttons;
    }

    public static JButton findButton(final Container root, final String text) {
        return find(root, JButton.class, button -> text.equals(button.getText()));
    }

    private static void collectButtons(final Container root, final List<JButton> buttons) {
        for (final Component child : root.getComponents()) {
            if (child instanceof JButton button) {
                buttons.add(button);
            }
            if (child instanceof Container container) {
                collectButtons(container, buttons);
            }
        }
    }
}
