package com.jagent.desktop.ui.components.terminal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.TransferHandler;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class AppTerminalPanelTest {
    @Test
    void rejectsUnsupportedTransferData() {
        final AppTerminalPanel panel = panel();
        final TransferHandler handler = panel.getTransferHandler();
        final TransferHandler.TransferSupport support =
                new TransferHandler.TransferSupport(new JPanel(), new StringSelection("text"));

        assertFalse(handler.canImport(support), "non-file-list transfers should be rejected");
        assertFalse(handler.importData(support), "unsupported transfers should not be imported");
    }

    @Test
    void reportsModifiedClickAndResetsCallback() {
        final List<Boolean> states = new ArrayList<>();
        final AppTerminalPanel panel = panel(states::add);
        final MouseEvent event =
                new MouseEvent(
                        panel,
                        MouseEvent.MOUSE_CLICKED,
                        System.currentTimeMillis(),
                        MouseEvent.CTRL_DOWN_MASK,
                        1,
                        1,
                        1,
                        false,
                        MouseEvent.BUTTON1);

        panel.processMouseEvent(event);

        assertTrue(states.contains(Boolean.TRUE), "modified clicks should activate links");
        assertTrue(Boolean.FALSE.equals(states.getLast()), "modified click state should reset");
    }

    private static AppTerminalPanel panel() {
        return panel(ignored -> {});
    }

    private static AppTerminalPanel panel(final java.util.function.Consumer<Boolean> callback) {
        final SettingsProvider settings = new AppTerminalSettings();
        return GuiActionRunner.execute(
                () ->
                        new AppTerminalPanel(
                                settings,
                                new TerminalTextBuffer(80, 24, new StyleState()),
                                new StyleState(),
                                callback,
                                ignored -> {}));
    }
}
