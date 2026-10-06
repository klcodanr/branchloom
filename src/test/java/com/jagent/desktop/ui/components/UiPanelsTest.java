package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JLabel;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;

class UiPanelsTest {
    @Test
    void createsLoadingMetricFormAndEmptyPanels() {
        final var loading = new LoadingPanel("Loading");
        final var form = new FormPanel("Name", new JTextArea());
        final var empty = new EmptyStatePanel("Nothing", "Try again");

        assertTrue(
                ((javax.swing.JProgressBar)
                                ((javax.swing.JPanel) loading.getComponent(0)).getComponent(0))
                        .isIndeterminate(),
                "loading progress should be indeterminate");
        assertEquals(2, form.getComponentCount(), "form components");
        assertEquals("Nothing", ((JLabel) empty.getComponent(1)).getText(), "empty title");
    }
}
