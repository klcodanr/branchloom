package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JButton;
import org.junit.jupiter.api.Test;

class BaseButtonTest {
    @Test
    void createsAccessibleButtonsAndActivatesWithKeyboard() {
        final JButton button = new BaseButton("Save");
        final int[] activations = {0};
        button.addActionListener(event -> activations[0]++);
        button.getActionMap().get("pressed").actionPerformed(null);

        assertEquals("Save", button.getAccessibleContext().getAccessibleName(), "button name");
        assertTrue(button.isFocusPainted(), "button should be focus painted");
        assertEquals(1, activations[0], "button activation count");
    }

    @Test
    void createsIconAndLinkButtons() {
        final JButton icon = new IconButton(null, "Open menu");
        final JButton link = new LinkButton("Open", () -> {});

        assertEquals("Open menu", icon.getToolTipText(), "icon button tooltip");
        assertEquals(32, icon.getPreferredSize().width, "icon button width");
        assertEquals("Open", link.getAccessibleContext().getAccessibleName(), "link name");
    }
}
