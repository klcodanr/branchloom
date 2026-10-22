package com.jagent.desktop.ui.components;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import javax.swing.Icon;
import javax.swing.Timer;

/** A button with a refresh icon that rotates in place. */
public final class RefreshButton extends IconButton {
    private final Icon refreshIcon = new RefreshIcon();
    private double angle;
    private final Timer refreshAnimation =
            new Timer(
                    75,
                    event -> {
                        this.rotate();
                        this.repaint();
                    });

    private final class RefreshIcon implements Icon {
        private final Icon delegate = UiIcons.refresh();

        @Override
        public int getIconWidth() {
            return delegate.getIconWidth();
        }

        @Override
        public int getIconHeight() {
            return delegate.getIconHeight();
        }

        @Override
        public void paintIcon(
                final Component component, final Graphics graphics, final int x, final int y) {
            final Graphics2D graphics2d = (Graphics2D) graphics.create();
            graphics2d.setRenderingHint(
                    RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            final AffineTransform transform =
                    AffineTransform.getRotateInstance(
                            angle, x + getIconWidth() / 2.0, y + getIconHeight() / 2.0);
            graphics2d.transform(transform);
            delegate.paintIcon(component, graphics2d, x, y);
            graphics2d.dispose();
        }
    }

    public RefreshButton() {
        super(UiIcons.refresh(), "Refresh");
        this.setIcon(refreshIcon);
        this.addActionListener(event -> this.start());
    }

    public void start() {
        reset();
        refreshAnimation.start();
    }

    public void stop() {
        refreshAnimation.stop();
        reset();
    }

    private void rotate() {
        angle += Math.PI / 12;
        if (angle >= Math.PI * 2) {
            angle -= Math.PI * 2;
        }
    }

    private void reset() {
        angle = 0;
        this.repaint();
    }
}
