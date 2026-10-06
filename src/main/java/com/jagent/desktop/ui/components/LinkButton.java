package com.jagent.desktop.ui.components;

import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.KeyStroke;

/** Borderless text button for navigation and external links. */
public final class LinkButton extends JButton {
    public LinkButton(final String text, final Runnable action) {
        super(text);
        putClientProperty("JButton.buttonType", "borderless");
        setBorderPainted(false);
        setContentAreaFilled(true);
        setFocusable(true);
        setFocusPainted(true);
        setRolloverEnabled(true);
        setMargin(UiConstants.ZERO_INSETS);
        setHorizontalAlignment(LEFT);
        getAccessibleContext().setAccessibleName(text);
        addActionListener(event -> action.run());
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
