package com.jagent.desktop.ui.components;

import com.jagent.desktop.services.terminal.TerminalState;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.UIManager;

public final class StatusDot extends JComponent {
    private Color color;

    public StatusDot(final Color color) {
        super();
        init();
        this.color = color;
    }

    public StatusDot(final Color color, final String tooltipText) {
        super();
        init();
        this.color = color;
        this.setToolTipText(tooltipText);
    }

    private void init() {
        setPreferredSize(new Dimension(10, 10));
        setMinimumSize(new Dimension(10, 10));
        setMaximumSize(new Dimension(10, 10));
    }

    private static Color terminalColor(final TerminalState state) {
        if (state == null) {
            return Theme.mutedColor();
        }
        return switch (state) {
            case STARTING -> Theme.warningColor();
            case WORKING -> UIManager.getColor("Component.focusColor");
            case IDLE -> Theme.successColor();
            case EXITED, STOPPED -> UIManager.getColor(UiConstants.DISABLED_FOREGROUND);
            case FAILED -> Theme.dangerColor();
        };
    }

    public static Icon terminalIcon(final TerminalState state) {
        return new TerminalIcon(terminalColor(state));
    }

    private void setColor(final Color color) {
        this.color = color;
        repaint();
    }

    public void update(final Color color, final String tooltip) {
        this.setToolTipText(tooltip);
        this.setColor(color);
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        if (color != null) {
            graphics.setColor(color);
            graphics.fillOval(1, 1, getWidth() - 2, getHeight() - 2);
        }
    }

    private static final class TerminalIcon implements Icon {
        private final Color color;

        private TerminalIcon(final Color color) {
            this.color = color;
        }

        @Override
        public int getIconWidth() {
            return 10;
        }

        @Override
        public int getIconHeight() {
            return 10;
        }

        @Override
        public void paintIcon(
                final Component component, final Graphics graphics, final int x, final int y) {
            if (color != null) {
                graphics.setColor(color);
                graphics.fillOval(x + 1, y + 1, 8, 8);
            }
        }
    }
}
