package com.jagent.desktop.ui.components;

import com.jagent.desktop.services.terminal.TerminalState;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JComponent;
import javax.swing.UIManager;

/** Creates and updates status indicators. */
public final class StatusDots {
    private StatusDots() {}

    public static JComponent terminal(final TerminalState state) {
        final StatusDot dot = new StatusDot();
        updateTerminal(dot, state);
        return dot;
    }

    public static void updateTerminal(final JComponent dot, final TerminalState state) {
        final Color color =
                switch (state) {
                    case STARTING -> Theme.warningColor();
                    case WORKING -> UIManager.getColor("Component.focusColor");
                    case IDLE -> Theme.successColor();
                    case EXITED, STOPPED -> UIManager.getColor(UiConstants.DISABLED_FOREGROUND);
                    case FAILED -> Theme.dangerColor();
                };
        update(dot, color, state.label());
    }

    public static JComponent create(final Color color, final String tooltip) {
        final StatusDot dot = new StatusDot();
        update(dot, color, tooltip);
        return dot;
    }

    public static void update(final JComponent dot, final Color color, final String tooltip) {
        dot.setToolTipText(tooltip);
        if (dot instanceof StatusDot statusDot) {
            statusDot.setColor(color);
        }
    }

    private static final class StatusDot extends JComponent {
        private Color color;

        private StatusDot() {
            super();
            setPreferredSize(new Dimension(10, 10));
            setMinimumSize(new Dimension(10, 10));
            setMaximumSize(new Dimension(10, 10));
        }

        private void setColor(final Color color) {
            this.color = color;
            repaint();
        }

        @Override
        protected void paintComponent(final Graphics graphics) {
            super.paintComponent(graphics);
            if (color != null) {
                graphics.setColor(color);
                graphics.fillOval(1, 1, getWidth() - 2, getHeight() - 2);
            }
        }
    }
}
