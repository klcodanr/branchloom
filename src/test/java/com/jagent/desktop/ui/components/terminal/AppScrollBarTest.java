package com.jagent.desktop.ui.components.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.Dimension;
import javax.swing.DefaultBoundedRangeModel;
import org.junit.jupiter.api.Test;

class AppScrollBarTest {
    @Test
    void hidesWhenContentFitsAndUsesNormalSizeWhenContentOverflows() {
        final var scrollBar = new AppScrollBar();

        scrollBar.setModel(new DefaultBoundedRangeModel(0, 100, 0, 100));
        assertEquals(
                new Dimension(0, 0),
                scrollBar.getPreferredSize(),
                "a scrollbar should collapse when all content is visible");

        scrollBar.setModel(new DefaultBoundedRangeModel(0, 100, 0, 101));
        assertNotEquals(
                new Dimension(0, 0),
                scrollBar.getPreferredSize(),
                "a scrollbar should retain its size when content overflows");
    }
}
