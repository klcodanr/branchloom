package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Container;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.assertj.swing.edt.GuiActionRunnable;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class SettingsPanelUiTest {
    @Test
    void saveButtonInvokesSaveCallbackWithoutAWindow() {
        final AtomicInteger saves = new AtomicInteger();
        final JPanel form = new JPanel();
        form.add(new JTextField("value"));
        final JPanel screen =
                GuiActionRunner.execute(
                        () ->
                                SettingsPanel.render(
                                        "Settings",
                                        "Description",
                                        form,
                                        saves::incrementAndGet,
                                        () -> {},
                                        () -> false));

        final var actions = (Container) screen.getComponent(2);
        final var save = (JButton) actions.getComponent(1);
        GuiActionRunner.execute((GuiActionRunnable) save::doClick);

        assertEquals(1, saves.get(), "save button should invoke callback");
    }

    @Test
    void labeledFieldWrapsTextAreasForEditing() {
        final var area = new javax.swing.JTextArea();

        final JPanel field =
                GuiActionRunner.execute(() -> SettingsPanel.labeledField("Prompt", area));

        assertEquals(2, field.getComponentCount(), "field should include label and scroll pane");
        assertEquals(true, area.getLineWrap(), "text area should wrap lines");
        assertEquals(true, area.getWrapStyleWord(), "text area should wrap words");
    }
}
