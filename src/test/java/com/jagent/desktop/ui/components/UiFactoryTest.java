package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;

class UiFactoryTest {
    private static final String VALUE_MESSAGE = "factory value should match";
    private static final String CONDITION_MESSAGE = "factory condition should hold";

    @Test
    void createsConfiguredTextAndButtons() {
        final var text = UiFactory.selectableText(null, Theme.FontSize.MD);
        final var html = UiFactory.selectableHtml(null, Theme.FontSize.SM);
        final JButton button = UiFactory.button("Save");
        final JButton iconButton = UiFactory.iconButton(new UiFactory.MenuIcon(Color.BLUE));

        assertEquals("", text.getText(), VALUE_MESSAGE);
        assertFalse(text.isEditable(), CONDITION_MESSAGE);
        assertTrue(html.getText().contains("<html>"), CONDITION_MESSAGE);
        assertFalse(html.isEditable(), CONDITION_MESSAGE);
        assertEquals("Save", button.getAccessibleContext().getAccessibleName(), VALUE_MESSAGE);
        assertEquals(22, iconButton.getPreferredSize().width, VALUE_MESSAGE);
    }

    @Test
    void buildsLoadingMetricsFormsAndEmptyPanels() {
        final var loading = UiFactory.loading("Loading");
        final var inline = UiFactory.inlineLoading("Wait");
        final var metric = UiFactory.metric("Count", "4");
        final var form = UiFactory.form("Name", new JTextArea());
        final var empty = UiFactory.empty("Nothing", "Try again");

        final JProgressBar progress =
                (JProgressBar) ((javax.swing.JPanel) loading.getComponent(0)).getComponent(0);
        assertTrue(progress.isIndeterminate(), CONDITION_MESSAGE);
        assertEquals(180, progress.getPreferredSize().width, VALUE_MESSAGE);
        assertEquals(8, progress.getPreferredSize().height, VALUE_MESSAGE);
        assertEquals(
                13,
                ((JLabel) ((javax.swing.JPanel) loading.getComponent(0)).getComponent(2))
                        .getFont()
                        .getSize(),
                VALUE_MESSAGE);
        assertEquals(2, inline.getComponentCount(), VALUE_MESSAGE);
        assertEquals(3, metric.getComponentCount(), VALUE_MESSAGE);
        assertEquals(2, form.getComponentCount(), VALUE_MESSAGE);
        assertEquals(5, empty.getComponentCount(), VALUE_MESSAGE);
        assertEquals("Nothing", ((JLabel) empty.getComponent(1)).getText(), VALUE_MESSAGE);
    }
}
