package com.jagent.desktop.test;

import java.awt.Component;
import java.awt.Container;
import java.util.function.Predicate;
import javax.swing.JButton;

/** Shared Swing component and EDT helpers for headless UI tests. */
public final class SwingTestSupport {
    private SwingTestSupport() {}

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

    public static JButton findButton(final Container root, final String text) {
        return find(root, JButton.class, button -> text.equals(button.getText()));
    }
}
