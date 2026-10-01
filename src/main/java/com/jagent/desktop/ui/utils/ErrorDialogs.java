package com.jagent.desktop.ui.utils;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ErrorDialogs {
    private static final Logger LOG = LoggerFactory.getLogger(ErrorDialogs.class);

    private ErrorDialogs() {}

    public static void show(final Component owner, final String title, final String message) {
        LOG.error("{}: {}", title, message);
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        final Runnable action =
                () ->
                        JOptionPane.showMessageDialog(
                                owner, message, title, JOptionPane.ERROR_MESSAGE);
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
