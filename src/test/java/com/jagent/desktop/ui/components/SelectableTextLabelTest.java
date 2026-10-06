package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class SelectableTextLabelTest {
    @Test
    void createsSelectableTextWithNullAsEmpty() {
        final var text = new SelectableTextLabel(null, Theme.FontSize.MD);

        assertEquals("", text.getText(), "null text should become empty");
        assertFalse(text.isEditable(), "selectable text should be read-only");
    }
}
