package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class PresentationHelpersTest {
    @Test
    void createsAnAppIconAtTheRequestedSize() {
        final BufferedImage image = AppIcon.image(32);
        assertEquals(32, image.getWidth(), "icon width should match requested size");
        assertEquals(32, image.getHeight(), "icon height should match requested size");
    }

    @Test
    void wrapsContentWithTheStandardInset() {
        final JLabel content = new JLabel("Content");
        final JPanel body = TabBody.wrap(content);
        assertSame(content, body.getComponent(0), "wrapped panel should contain content");
        assertEquals(TabBody.INSET, body.getInsets().top, "top inset should match standard inset");
    }

    @Test
    void displayTextHelpersFormatValues() {
        assertEquals(
                "a&amp;b&lt;c&gt;",
                UiText.escapeHtml("a&b<c>"),
                "escapeHtml should encode special characters");
        assertEquals(
                "#0a14ff",
                UiText.colorHex(new Color(10, 20, 255)),
                "colorHex should convert RGB values");
        assertEquals(
                "Ready For Review",
                UiText.titleCase("READY_FOR_REVIEW"),
                "titleCase should format check status tokens");
        assertEquals(
                "Ready Review",
                UiText.titleCase("READY__REVIEW"),
                "titleCase should collapse repeated separators");
        assertEquals("", UiText.titleCase("___"), "titleCase should handle separator-only tokens");
    }
}
