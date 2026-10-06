package com.jagent.desktop.ui.components;

import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.KeyStroke;

/** Standard application button with accessible text and keyboard activation. */
@SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
public class BaseButton extends JButton {
    public BaseButton(final String text) {
        super(text);
        configure(text);
    }

    public BaseButton(final String text, final Icon icon) {
        super(text, icon);
        configure(text);
    }

    public BaseButton(final Icon icon) {
        super(icon);
        configure(null);
    }

    private void configure(final String accessibleName) {
        if (accessibleName != null) {
            getAccessibleContext().setAccessibleName(accessibleName);
        }
        setFocusPainted(true);
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
