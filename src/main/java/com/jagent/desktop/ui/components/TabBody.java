package com.jagent.desktop.ui.components;

import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Standard content inset for application tab bodies. */
public final class TabBody {
    public static final int INSET = UiConstants.TAB_INSET;

    private TabBody() {}

    public static JPanel wrap(final JComponent content) {
        final JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(INSET, INSET, INSET, INSET));
        body.add(content, BorderLayout.CENTER);
        return body;
    }
}
