package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.Icon;
import org.junit.jupiter.api.Test;

class RotatingIconTest {
    @Test
    void delegatesDimensionsAndPaintingWhileRotating() {
        final AtomicInteger paints = new AtomicInteger();
        final Icon delegate =
                new Icon() {
                    @Override
                    public int getIconWidth() {
                        return 12;
                    }

                    @Override
                    public int getIconHeight() {
                        return 10;
                    }

                    @Override
                    public void paintIcon(
                            final Component component,
                            final Graphics graphics,
                            final int x,
                            final int y) {
                        paints.incrementAndGet();
                    }
                };
        final RotatingIcon icon = new RotatingIcon(delegate);

        assertEquals(12, icon.getIconWidth(), "icon width should delegate");
        assertEquals(10, icon.getIconHeight(), "icon height should delegate");
        final BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        icon.paintIcon(null, image.getGraphics(), 2, 3);
        icon.rotate();
        icon.paintIcon(null, image.getGraphics(), 2, 3);
        icon.reset();

        assertEquals(2, paints.get(), "rotating icon should paint its delegate");
    }
}
