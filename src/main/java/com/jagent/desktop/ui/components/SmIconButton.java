package com.jagent.desktop.ui.components;

import java.awt.Dimension;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;

/** A small icon-only toggle button styled for use in a segmented control group. */
public final class SmIconButton extends JToggleButton {
    public SmIconButton(final String tooltip, final Icon icon) {
        super(icon);
        setToolTipText(tooltip);
        getAccessibleContext().setAccessibleName(tooltip);
        putClientProperty("JButton.buttonType", "segmented");
        putClientProperty("JButton.segmentPosition", "only");
        setPreferredSize(new Dimension(32, 32));
        final KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        final KeyStroke space = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0);
        getInputMap(WHEN_FOCUSED).put(enter, "pressed");
        getInputMap(WHEN_FOCUSED).put(space, "pressed");
        getActionMap()
                .put(
                        "pressed",
                        new AbstractAction() {
                            @Override
                            public void actionPerformed(final java.awt.event.ActionEvent event) {
                                doClick();
                            }
                        });
    }
}
