package com.jagent.desktop.ui.components;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import javax.swing.Icon;

/** An icon that rotates in place when repainted. */
final class RotatingIcon implements Icon {
    private final Icon delegate;
    private double angle;

    public RotatingIcon(final Icon delegate) {
        this.delegate = delegate;
    }

    public void rotate() {
        angle += Math.PI / 12;
        if (angle >= Math.PI * 2) {
            angle -= Math.PI * 2;
        }
    }

    public void reset() {
        angle = 0;
    }

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
