package com.jagent.desktop.ui.components;

import javax.swing.Icon;
import javax.swing.JToggleButton;

/** A small icon-only toggle button styled for use in a segmented control group. */
public final class SmIconButton extends JToggleButton {
    public SmIconButton(final String tooltip, final Icon icon) {
        super(icon);
        setToolTipText(tooltip);
        getAccessibleContext().setAccessibleName(tooltip);
        putClientProperty("JButton.buttonType", "segmented");
        putClientProperty("JButton.segmentPosition", "only");
    }
}
