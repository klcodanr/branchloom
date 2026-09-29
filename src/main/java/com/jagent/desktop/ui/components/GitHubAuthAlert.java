package com.jagent.desktop.ui.components;

import com.jagent.desktop.services.GitHubAuthException;
import java.awt.Component;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Presents safe guidance when a GitHub credential cannot be used. */
public final class GitHubAuthAlert {
    private GitHubAuthAlert() {}

    public static void show(final Component parent, final GitHubAuthException exception) {
        showMessage(parent, exception.getMessage(), "GitHub authentication required");
    }

    public static void show(final Component parent, final Exception exception) {
        if (exception instanceof GitHubAuthException authException) {
            show(parent, authException);
        }
    }

    private static void showMessage(
            final Component parent, final String message, final String title) {
        final Runnable action =
                () ->
                        JOptionPane.showMessageDialog(
                                parent, message, title, JOptionPane.ERROR_MESSAGE);
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
