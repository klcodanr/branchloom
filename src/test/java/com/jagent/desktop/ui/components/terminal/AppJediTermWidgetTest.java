package com.jagent.desktop.ui.components.terminal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.util.SystemInfo;
import com.jediterm.terminal.TerminalCopyPasteHandler;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.ui.TerminalPanel;
import java.awt.event.KeyEvent;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class AppJediTermWidgetTest {
    private static final Path TEMP_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"));
    private static final String SELECTED_TEXT = "selected text";

    @Test
    void createsApplicationTerminalComponents() {
        final AppJediTermWidget widget =
                GuiActionRunner.execute(
                        () ->
                                new AppJediTermWidget(
                                        80,
                                        24,
                                        new AppTerminalSettings(),
                                        TEMP_DIRECTORY,
                                        ignored -> {},
                                        new com.jagent.desktop.services.dialog
                                                .HeadlessDialogService()));

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
                                        ignored -> {},
                                        new com.jagent.desktop.services.dialog
                                                .HeadlessDialogService()));
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

    @Test
    void copiesSavedSelectionWithThePlatformMenuShortcut() throws ReflectiveOperationException {
        final AppJediTermWidget widget =
                GuiActionRunner.execute(
                        () ->
                                new AppJediTermWidget(
                                        80,
                                        24,
                                        new AppTerminalSettings(),
                                        TEMP_DIRECTORY,
                                        ignored -> {},
                                        new com.jagent.desktop.services.dialog
                                                .HeadlessDialogService()));
        final AppTerminalPanel panel =
                assertInstanceOf(AppTerminalPanel.class, widget.getTerminalPanel());
        final AtomicReference<String> copiedText = new AtomicReference<>();
        setCopyPasteHandler(panel, copiedText);
        panel.getTerminalTextBuffer().writeString(1, 1, new CharBuffer(SELECTED_TEXT));
        panel.selectAll();
        panel.scrollArea(0, panel.getTerminalTextBuffer().getHeight(), 1);
        final int shortcut = SystemInfo.isMacOS ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK;
        final KeyEvent copy =
                new KeyEvent(
                        panel,
                        KeyEvent.KEY_PRESSED,
                        System.currentTimeMillis(),
                        shortcut,
                        KeyEvent.VK_C,
                        'c');

        GuiActionRunner.execute(() -> panel.handleKeyEvent(copy));

        assertTrue(copy.isConsumed(), "the menu shortcut should copy the saved selection");
        assertTrue(
                copiedText.get().contains(SELECTED_TEXT), "the saved selection should be copied");
        widget.close();
    }

    @Test
    void consumesInterruptAndLiteralNewlineWhenAStarterIsAttached() {
        final AppJediTermWidget widget =
                GuiActionRunner.execute(
                        () ->
                                new AppJediTermWidget(
                                        80,
                                        24,
                                        new AppTerminalSettings(),
                                        TEMP_DIRECTORY,
                                        ignored -> {},
                                        new com.jagent.desktop.services.dialog
                                                .HeadlessDialogService()));
        widget.setTtyConnector(new StubTtyConnector());
        final TerminalPanel panel = widget.getTerminalPanel();
        final KeyEvent interrupt =
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

        GuiActionRunner.execute(
                () -> {
                    panel.handleKeyEvent(interrupt);
                    panel.handleKeyEvent(newline);
                });

        assertTrue(interrupt.isConsumed(), "Ctrl+C should send the terminal interrupt");
        assertTrue(newline.isConsumed(), "Shift+Enter should send a literal newline");
        widget.close();
    }

    private static final class StubTtyConnector implements TtyConnector {
        @Override
        public int read(final char[] buffer, final int offset, final int length) {
            return -1;
        }

        @Override
        public void write(final byte[] bytes) {}

        @Override
        public void write(final String string) {}

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean ready() {
            return false;
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public void close() {}
    }

    private static void setCopyPasteHandler(
            final AppTerminalPanel panel, final AtomicReference<String> copiedText)
            throws ReflectiveOperationException {
        MethodHandles.privateLookupIn(AppTerminalPanel.class, MethodHandles.lookup())
                .findVarHandle(
                        AppTerminalPanel.class, "copyPasteHandler", TerminalCopyPasteHandler.class)
                .set(
                        panel,
                        new TerminalCopyPasteHandler() {
                            @Override
                            public void setContents(
                                    final String text, final boolean useSystemSelection) {
                                copiedText.set(text);
                            }

                            @Override
                            public String getContents(final boolean useSystemSelection) {
                                return "";
                            }
                        });
    }
}
