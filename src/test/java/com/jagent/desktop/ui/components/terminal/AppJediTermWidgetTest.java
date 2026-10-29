package com.jagent.desktop.ui.components.terminal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jediterm.terminal.ui.TerminalPanel;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import javax.swing.JPanel;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class AppJediTermWidgetTest {
    private static final Path TEMP_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"));

    @Test
    void createsApplicationTerminalComponents() {
        final JPanel owner = new JPanel();
        final AppJediTermWidget widget =
                GuiActionRunner.execute(
                        () ->
                                new AppJediTermWidget(
                                        80,
                                        24,
                                        new AppTerminalSettings(),
                                        TEMP_DIRECTORY,
                                        owner,
                                        ignored -> {}));

        assertInstanceOf(
                AppTerminalPanel.class,
                widget.getTerminalPanel(),
                "widget should create the application terminal panel");
        assertNotNull(
                widget.getTerminalPanel().getVerticalScrollModel(),
                "widget should initialize a terminal scroll model");
        widget.close();
    }

    @Test
    void ignoresShortcutsWhenNoTerminalStarterIsAttached() {
        final AppJediTermWidget widget =
                GuiActionRunner.execute(
                        () ->
                                new AppJediTermWidget(
                                        80,
                                        24,
                                        new AppTerminalSettings(),
                                        TEMP_DIRECTORY,
                                        new JPanel(),
                                        ignored -> {}));
        final TerminalPanel panel = widget.getTerminalPanel();

        final KeyEvent copy =
                new KeyEvent(
                        panel,
                        KeyEvent.KEY_PRESSED,
                        System.currentTimeMillis(),
                        KeyEvent.CTRL_DOWN_MASK,
                        KeyEvent.VK_C,
                        'c');
        final KeyEvent newline =
                new KeyEvent(
                        panel,
                        KeyEvent.KEY_PRESSED,
                        System.currentTimeMillis(),
                        KeyEvent.SHIFT_DOWN_MASK,
                        KeyEvent.VK_ENTER,
                        '\n');
        final KeyEvent ordinary =
                new KeyEvent(
                        panel,
                        KeyEvent.KEY_PRESSED,
                        System.currentTimeMillis(),
                        0,
                        KeyEvent.VK_A,
                        'a');

        GuiActionRunner.execute(
                () -> {
                    panel.handleKeyEvent(copy);
                    panel.handleKeyEvent(newline);
                    panel.handleKeyEvent(ordinary);
                });

        assertFalse(copy.isConsumed(), "copy should remain available without a terminal starter");
        assertFalse(newline.isConsumed(), "newline should remain available without a starter");
        assertFalse(ordinary.isConsumed(), "ordinary keys should not be consumed");
        widget.close();
    }
}
