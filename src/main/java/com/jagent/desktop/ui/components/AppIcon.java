package com.jagent.desktop.ui.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class AppIcon {
    private AppIcon() {}

    public static BufferedImage image(final int size) {
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        final float scale = size / 128f;
        graphics.scale(scale, scale);
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.decode("#191e27"));
        graphics.fillRoundRect(0, 0, 128, 128, 28, 28);
        graphics.setColor(Color.decode("#6f89ff"));
        graphics.setStroke(new BasicStroke(10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(35, 91, 35, 48);
        graphics.drawLine(35, 48, 53, 30);
        graphics.drawLine(53, 30, 75, 30);
        graphics.drawLine(53, 72, 71, 54);
        graphics.drawLine(71, 54, 91, 54);
        node(graphics, 35, 94, "#5468be");
        node(graphics, 92, 30, "#7389ff");
        node(graphics, 92, 54, "#5468be");
        graphics.dispose();
        return image;
    }

    private static void node(
            final Graphics2D graphics, final int x, final int y, final String fill) {
        graphics.setColor(Color.decode(fill));
        graphics.fillOval(x - 12, y - 12, 24, 24);
        graphics.setColor(Color.decode("#e8edf5"));
        graphics.setStroke(new BasicStroke(5));
        graphics.drawOval(x - 12, y - 12, 24, 24);
    }
}
