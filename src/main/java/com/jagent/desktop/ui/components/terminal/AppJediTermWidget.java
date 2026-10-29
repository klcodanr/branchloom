package com.jagent.desktop.ui.components.terminal;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.Tool;
import com.jagent.desktop.services.EditorCommands;
import com.jagent.desktop.services.EditorDetection;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.ui.components.UiText;
import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermSearchComponent;
import com.jediterm.terminal.ui.JediTermWidget;
import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;

public final class AppJediTermWidget extends JediTermWidget {
    private final Component owner;
    private final Path directory;
    private boolean modifiedClickLinkActivationAllowed;
    private boolean contextMenuLinkActivationAllowed;

    public AppJediTermWidget(
            final int columns,
            final int rows,
            final AppTerminalSettings settings,
            final Path directory,
            final JPanel owner,
            final Consumer<String> titleChanged) {
        super(columns, rows, settings);
        this.directory = directory;
        this.owner = owner;
        myTerminal.addApplicationTitleListener(titleChanged::accept);
        addHyperlinkFilter(
                new TerminalLinkFilter(PlatformCommands::openUrl, this::isLinkActivationAllowed));
        addHyperlinkFilter(
                new TerminalFileLinkFilter(directory, this::open, this::isLinkActivationAllowed));
        getTerminalPanel().setDefaultCursorShape(CursorShape.STEADY_BLOCK);
        getTerminalPanel()
                .addCustomKeyListener(
                        new KeyAdapter() {
                            @Override
                            public void keyPressed(final KeyEvent event) {
                                if (event.isControlDown()
                                        && event.getKeyCode() == KeyEvent.VK_C
                                        && getTerminalPanel().getSelection() == null) {
                                    final var starter = getTerminalStarter();
                                    if (starter != null) {
                                        starter.sendBytes(new byte[] {0x03}, true);
                                        event.consume();
                                    }
                                } else if (isLiteralNewlineShortcut(event)) {
                                    final var starter = getTerminalStarter();
                                    if (starter != null) {
                                        starter.sendBytes(new byte[] {0x0A}, true);
                                        event.consume();
                                    }
                                }
                            }
                        });
    }

    private boolean isLiteralNewlineShortcut(final KeyEvent event) {
        return event.getKeyCode() == KeyEvent.VK_ENTER
                && (event.isControlDown() || event.isShiftDown());
    }

    public void open(final TerminalFileLink link) {
        final var editors = EditorDetection.detect();
        if (editors.isEmpty()) {
            JOptionPane.showMessageDialog(
                    owner,
                    "No supported editor is configured.",
                    "Open file",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        final Tool editor = editors.getFirst();
        BackgroundOperations.runCommand(
                        "Commands",
                        "open-file-link",
                        EditorCommands.openFile(editor, link.path(), link.line(), link.column()),
                        directory,
                        null)
                .exceptionally(
                        exception -> {
                            final String message =
                                    exception.getCause() == null
                                            ? exception.getMessage()
                                            : exception.getCause().getMessage();
                            JOptionPane.showMessageDialog(
                                    owner,
                                    UiText.valueOrDefault(message, "Editor failed."),
                                    "Open file",
                                    JOptionPane.ERROR_MESSAGE);
                            return null;
                        });
    }

    private boolean isLinkActivationAllowed() {
        return modifiedClickLinkActivationAllowed || contextMenuLinkActivationAllowed;
    }

    @Override
    protected JScrollBar createScrollBar() {
        final JScrollBar bar = new AppScrollBar();
        bar.setUnitIncrement(16);
        return bar;
    }

    @Override
    protected com.jediterm.terminal.ui.TerminalPanel createTerminalPanel(
            final com.jediterm.terminal.ui.settings.SettingsProvider settings,
            final StyleState styleState,
            final TerminalTextBuffer textBuffer) {
        return new AppTerminalPanel(
                settings,
                textBuffer,
                styleState,
                allowed -> modifiedClickLinkActivationAllowed = allowed,
                allowed -> contextMenuLinkActivationAllowed = allowed);
    }

    @Override
    protected JediTermSearchComponent createSearchComponent() {
        return new AppSearchComponent();
    }
}
