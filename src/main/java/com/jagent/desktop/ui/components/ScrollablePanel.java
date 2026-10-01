package com.jagent.desktop.ui.components;

import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/** A panel that fills the viewport width while retaining vertical scrolling. */
public abstract class ScrollablePanel extends JPanel implements Scrollable {
    protected ScrollablePanel() {
        super();
    }

    protected ScrollablePanel(final LayoutManager layout) {
        super(layout);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(
            final Rectangle visibleRect, final int orientation, final int direction) {
        return UiConstants.SPACING_MD;
    }

    @Override
    public int getScrollableBlockIncrement(
            final Rectangle visibleRect, final int orientation, final int direction) {
        return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
