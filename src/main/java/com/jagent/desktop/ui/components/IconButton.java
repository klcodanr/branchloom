package com.jagent.desktop.ui.components;

import java.awt.Dimension;
import javax.swing.Icon;

/** Compact toolbar button represented by an icon. */
public class IconButton extends BaseButton {
    public IconButton(final Icon icon, final String accessibleName) {
        super(icon);
        configure(this, accessibleName);
    }

    private static void configure(final javax.swing.JButton button, final String accessibleName) {
        if (accessibleName != null && !accessibleName.isBlank()) {
            button.setToolTipText(accessibleName);
            button.getAccessibleContext().setAccessibleName(accessibleName);
        }
        button.setBorderPainted(false);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setMargin(UiConstants.ZERO_INSETS);
        button.setPreferredSize(new Dimension(32, 32));
    }
}
