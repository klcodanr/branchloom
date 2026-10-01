package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.SwingConstants;
import org.junit.jupiter.api.Test;

class ScrollablePanelTest {
    private final ScrollablePanel panel = new ScrollablePanelFixture();

    private static final class ScrollablePanelFixture extends ScrollablePanel {}

    @Test
    void fillsViewportWidthAndSupportsVerticalScrolling() {
        final Rectangle visible = new Rectangle(0, 0, 100, 200);
        panel.setPreferredSize(new Dimension(100, 400));

        assertEquals(
                panel.getPreferredSize(),
                panel.getPreferredScrollableViewportSize(),
                "viewport size should use the panel preferred size");
        assertEquals(
                UiConstants.SPACING_MD,
                panel.getScrollableUnitIncrement(visible, 0, 1),
                "scroll unit should use the standard spacing");
        assertEquals(
                200,
                panel.getScrollableBlockIncrement(visible, SwingConstants.VERTICAL, 1),
                "vertical block scroll should use the visible height");
        assertEquals(
                100,
                panel.getScrollableBlockIncrement(visible, SwingConstants.HORIZONTAL, 1),
                "horizontal block scroll should use the visible width");
        assertTrue(panel.getScrollableTracksViewportWidth(), "panel should track viewport width");
        assertFalse(
                panel.getScrollableTracksViewportHeight(),
                "panel should retain vertical scrolling");
    }
}
