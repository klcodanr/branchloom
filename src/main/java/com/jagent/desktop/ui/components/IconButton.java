package com.jagent.desktop.ui.components;

import java.awt.Dimension;
import javax.swing.Icon;

/** Compact toolbar button represented by an icon. */
public final class IconButton extends BaseButton {
    public IconButton(final Icon icon, final String accessibleName) {
        super(icon);
        if (accessibleName != null && !accessibleName.isBlank()) {
            setToolTipText(accessibleName);
            getAccessibleContext().setAccessibleName(accessibleName);
        }
        setBorderPainted(false);
        putClientProperty("JButton.buttonType", "toolBarButton");
        setMargin(UiConstants.ZERO_INSETS);
        setPreferredSize(new Dimension(32, 32));
    }
}
