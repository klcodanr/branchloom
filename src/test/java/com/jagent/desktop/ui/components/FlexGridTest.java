package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Dimension;
import java.awt.GridBagLayout;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class FlexGridTest {
    @Test
    void addsComponentsAcrossRowsAndReturnsItself() {
        final FlexGrid grid = new FlexGrid(0);
        final JLabel first = new JLabel("first");
        final JLabel second = new JLabel("second");
        final JLabel third = new JLabel("third");

        assertSame(
                grid,
                grid.add(first, 8).add(second, 4).add(third, 6),
                "add should return the grid");

        final GridBagLayout layout = (GridBagLayout) grid.getLayout();
        assertEquals(0, layout.getConstraints(first).gridy, "first item should be on row zero");
        assertEquals(0, layout.getConstraints(second).gridy, "second item should share row zero");
        assertEquals(1, layout.getConstraints(third).gridy, "third item should be on row one");
        assertEquals(6, layout.getConstraints(third).gridwidth, "third item width should match");
    }

    @Test
    void startsANewRowWhenRequested() {
        final FlexGrid grid = new FlexGrid(0);
        final JLabel first = new JLabel("first");
        final JLabel second = new JLabel("second");

        assertSame(grid, grid.add(first, 4).row().add(second, 4), "row should return the grid");

        final GridBagLayout layout = (GridBagLayout) grid.getLayout();
        assertEquals(0, layout.getConstraints(first).gridy, "first item should be on row zero");
        assertEquals(1, layout.getConstraints(second).gridy, "second item should be on row one");
    }

    @Test
    void allowsComponentsToShrinkHorizontally() {
        final FlexGrid grid = new FlexGrid(0);
        final JLabel label = new JLabel("label");
        label.setMinimumSize(new Dimension(100, 20));

        grid.add(label, 12);

        assertEquals(0, label.getMinimumSize().width, "width should be unconstrained");
        assertEquals(20, label.getMinimumSize().height, "height should be preserved");
    }

    @Test
    void hasNoMinimumHorizontalSize() {
        final FlexGrid grid = new FlexGrid(0);

        assertEquals(0, grid.getMinimumSize().width, "grid should have no minimum width");
    }

    @Test
    void rejectsInvalidWidths() {
        final FlexGrid grid = new FlexGrid(0);
        assertThrows(
                IllegalArgumentException.class,
                () -> grid.add(new JLabel(), 0),
                "zero width should be rejected");
        assertThrows(
                IllegalArgumentException.class,
                () -> grid.add(new JLabel(), 13),
                "widths above twelve should be rejected");
    }
}
