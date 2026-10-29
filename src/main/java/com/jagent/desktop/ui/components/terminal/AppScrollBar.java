package com.jagent.desktop.ui.components.terminal;

import java.awt.Dimension;
import javax.swing.BoundedRangeModel;
import javax.swing.JScrollBar;

public final class AppScrollBar extends JScrollBar {
    @Override
    public Dimension getPreferredSize() {
        final BoundedRangeModel model = getModel();
        if (model.getMaximum() - model.getMinimum() <= model.getExtent()) {
            return new Dimension(0, 0);
        }
        return super.getPreferredSize();
    }

    @Override
    public void setModel(final BoundedRangeModel model) {
        super.setModel(model);
        model.addChangeListener(
                ignored -> {
                    revalidate();
                    if (getParent() != null) {
                        getParent().revalidate();
                    }
                });
    }
}
