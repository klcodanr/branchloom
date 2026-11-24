package com.jagent.desktop.ui.components.terminal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jediterm.terminal.TerminalCopyPasteHandler;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.TerminalAction;
import com.jediterm.terminal.ui.TerminalActionPresentation;
import com.jediterm.terminal.ui.TerminalActionProvider;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.TransferHandler;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class AppTerminalPanelTest {
    private static final String SELECTED_TEXT = "selected text";

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

    @Test
    void keepsCopyEnabledWhenTerminalScrollClearsItsSelection() {
        final AppTerminalPanel panel = panel();
        panel.getTerminalTextBuffer().writeString(1, 1, new CharBuffer(SELECTED_TEXT));
        panel.selectAll();

        panel.scrollArea(0, panel.getTerminalTextBuffer().getHeight(), 1);

        final JMenuItem copy = copyItem(panel.createPopupMenu(copyProvider()));
        assertTrue(copy.isEnabled(), "copy should remain available after a terminal scroll");
    }

    @Test
    void copiesSavedSelectionFromThePopupAfterTerminalScroll() throws ReflectiveOperationException {
        final AppTerminalPanel panel = panel();
        final AtomicReference<String> copiedText = new AtomicReference<>();
        setCopyPasteHandler(panel, copiedText);
        panel.getTerminalTextBuffer().writeString(1, 1, new CharBuffer(SELECTED_TEXT));
        panel.selectAll();
        panel.scrollArea(0, panel.getTerminalTextBuffer().getHeight(), 1);

        copyItem(panel.createPopupMenu(copyProvider())).doClick();

        assertTrue(
                copiedText.get().contains(SELECTED_TEXT), "the saved selection should be copied");
    }

    @Test
    void reportsWhenThereIsNoSavedSelectionToCopy() {
        assertFalse(panel().copyLastSelection(), "copy should fail when no text was selected");
    }

    @Test
    void avoidsLookingUpLinksWhenPopupPointIsOutsideTheTerminal() {
        final AppTerminalPanel panel = panel();
        final MouseEvent event =
                new MouseEvent(
                        panel,
                        MouseEvent.MOUSE_MOVED,
                        System.currentTimeMillis(),
                        0,
                        10_000,
                        10_000,
                        0,
                        false,
                        MouseEvent.NOBUTTON);

        panel.processMouseEvent(event);

        assertFalse(
                copyItem(panel.createPopupMenu(copyProvider())).isEnabled(),
                "the copy action should remain disabled without a selection");
    }

    @Test
    void ignoresNonHyperlinkStylesAtAnInBoundsPopupPoint() throws ReflectiveOperationException {
        final AppTerminalPanel panel = panel();
        GuiActionRunner.execute(() -> panel.init(new javax.swing.JScrollBar()));
        final MouseEvent event =
                new MouseEvent(
                        panel,
                        MouseEvent.MOUSE_MOVED,
                        System.currentTimeMillis(),
                        0,
                        0,
                        0,
                        0,
                        false,
                        MouseEvent.NOBUTTON);

        panel.processMouseEvent(event);

        assertFalse(
                copyItem(panel.createPopupMenu(copyProvider())).isEnabled(),
                "an ordinary terminal cell should not be treated as a link");
    }

    @Test
    void clearsSavedCopyWhenTheUserStartsANewSelection() {
        final AppTerminalPanel panel = panel();
        panel.getTerminalTextBuffer().writeString(1, 1, new CharBuffer(SELECTED_TEXT));
        panel.selectAll();
        panel.scrollArea(0, panel.getTerminalTextBuffer().getHeight(), 1);
        final MouseEvent press =
                new MouseEvent(
                        panel,
                        MouseEvent.MOUSE_PRESSED,
                        System.currentTimeMillis(),
                        0,
                        1,
                        1,
                        1,
                        false,
                        MouseEvent.BUTTON1);

        panel.processMouseEvent(press);

        assertFalse(
                copyItem(panel.createPopupMenu(copyProvider())).isEnabled(),
                "starting a new selection should discard the prior copied text");
    }

    private static TerminalActionProvider copyProvider() {
        return new TerminalActionProvider() {
            @Override
            public List<TerminalAction> getActions() {
                return List.of(
                        new TerminalAction(
                                        new TerminalActionPresentation("Copy", List.of()),
                                        ignored -> true)
                                .withEnabledSupplier(() -> false));
            }

            @Override
            public TerminalActionProvider getNextProvider() {
                return null;
            }

            @Override
            public void setNextProvider(final TerminalActionProvider provider) {}
        };
    }

    private static JMenuItem copyItem(final JPopupMenu menu) {
        return java.util.Arrays.stream(menu.getComponents())
                .filter(JMenuItem.class::isInstance)
                .map(JMenuItem.class::cast)
                .filter(item -> "Copy".equals(item.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("terminal popup should include Copy"));
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

    private static AppTerminalPanel panel() {
        return panel(ignored -> {});
    }

    private static AppTerminalPanel panel(final java.util.function.Consumer<Boolean> callback) {
        final SettingsProvider settings = new AppTerminalSettings();
        final AppTerminalPanel panel =
                GuiActionRunner.execute(
                        () ->
                                new AppTerminalPanel(
                                        settings,
                                        new TerminalTextBuffer(80, 24, new StyleState()),
                                        new StyleState(),
                                        callback,
                                        ignored -> {}));
        panel.setDialogService(new com.jagent.desktop.services.dialog.HeadlessDialogService());
        return panel;
    }
}
