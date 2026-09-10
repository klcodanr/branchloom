package com.jagent.desktop.test;

import java.awt.Component;
import java.awt.Container;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javax.swing.JButton;
import org.assertj.swing.edt.GuiActionRunner;

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

    public static void await(final BooleanSupplier condition, final String message)
            throws InterruptedException {
        final long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (GuiActionRunner.execute(condition::getAsBoolean)) {
                return;
            }
            Thread.sleep(10);
        }
        if (!GuiActionRunner.execute(condition::getAsBoolean)) {
            throw new AssertionError(message);
        }
    }
}
