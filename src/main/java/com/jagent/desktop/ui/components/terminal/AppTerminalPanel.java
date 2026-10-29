package com.jagent.desktop.ui.components.terminal;

import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.components.UiPopupMenus;
import com.jagent.desktop.ui.utils.ClipboardImagePaster;
import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.TerminalCopyPasteHandler;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JPopupMenu;
import javax.swing.TransferHandler;

public final class AppTerminalPanel extends com.jediterm.terminal.ui.TerminalPanel {
    private transient TerminalStarter terminalStarter;
    private transient Point lastMousePoint;
    private final transient Consumer<Boolean> modifiedClickLinkActivationChanged;
    private final transient Consumer<Boolean> contextMenuLinkActivationChanged;
    private transient DialogService dialogService;

    public AppTerminalPanel(
            final SettingsProvider settings,
            final TerminalTextBuffer textBuffer,
            final StyleState styleState,
            final Consumer<Boolean> modifiedClickLinkActivationChanged,
            final Consumer<Boolean> contextMenuLinkActivationChanged) {
        super(settings, textBuffer, styleState);
        this.modifiedClickLinkActivationChanged = modifiedClickLinkActivationChanged;
        this.contextMenuLinkActivationChanged = contextMenuLinkActivationChanged;
        setTransferHandler(
                new TransferHandler() {
                    @Override
                    public boolean canImport(final TransferSupport support) {
                        return support.isDrop()
                                && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
                    }

                    @Override
                    public boolean importData(final TransferSupport support) {
                        if (!canImport(support) || terminalStarter == null) {
                            return false;
                        }
                        try {
                            final Object data =
                                    support.getTransferable()
                                            .getTransferData(DataFlavor.javaFileListFlavor);
                            if (!(data instanceof List<?> files) || files.size() != 1) {
                                return false;
                            }
                            if (!(files.getFirst() instanceof File file)) {
                                return false;
                            }
                            terminalStarter.sendString(
                                    file.toPath().toAbsolutePath().normalize().toString(), true);
                            return true;
                        } catch (IOException
                                | java.awt.datatransfer.UnsupportedFlavorException ignored) {
                            return false;
                        }
                    }
                });
    }

    public void setDialogService(final DialogService dialogService) {
        this.dialogService = dialogService;
    }

    private boolean isModifiedLinkActivationEvent(final java.awt.event.MouseEvent event) {
        return event.getID() == java.awt.event.MouseEvent.MOUSE_CLICKED
                && event.getButton() == java.awt.event.MouseEvent.BUTTON1
                && (event.isControlDown() || event.isMetaDown());
    }

    @Override
    protected void processMouseEvent(final java.awt.event.MouseEvent event) {
        lastMousePoint = event.getPoint();
        modifiedClickLinkActivationChanged.accept(isModifiedLinkActivationEvent(event));
        super.processMouseEvent(event);
        modifiedClickLinkActivationChanged.accept(false);
    }

    @Override
    protected JPopupMenu createPopupMenu(
            final com.jediterm.terminal.ui.TerminalActionProvider actionProvider) {
        final String link = linkAt(lastMousePoint);
        contextMenuLinkActivationChanged.accept(link != null);
        final JPopupMenu menu = super.createPopupMenu(actionProvider);
        UiPopupMenus.install(
                menu,
                this,
                visible -> {
                    if (link != null) {
                        contextMenuLinkActivationChanged.accept(visible);
                    }
                });
        return menu;
    }

    private String linkAt(final Point point) {
        if (point == null || myCharSize.width <= 0 || myCharSize.height <= 0) {
            return null;
        }
        final int column = Math.max(0, point.x / myCharSize.width);
        final int row = Math.max(0, point.y / myCharSize.height);
        if (row >= getTerminalTextBuffer().getHeight()
                || column >= getTerminalTextBuffer().getWidth()) {
            return null;
        }
        final var style = getTerminalTextBuffer().getStyleAt(column, row);
        if (!(style instanceof HyperlinkStyle hyperlink)) {
            return null;
        }
        return hyperlink.getLinkInfo() instanceof TerminalLinkInfo linkInfo
                ? linkInfo.value()
                : null;
    }

    @Override
    public void setTerminalStarter(final TerminalStarter starter) {
        super.setTerminalStarter(starter);
        terminalStarter = starter;
    }

    @Override
    protected TerminalCopyPasteHandler createCopyPasteHandler() {
        final TerminalCopyPasteHandler delegate = super.createCopyPasteHandler();
        return new TerminalCopyPasteHandler() {
            @Override
            public void setContents(final String text, final boolean useSystemSelection) {
                delegate.setContents(text, useSystemSelection);
            }

            @Override
            public String getContents(final boolean useSystemSelection) {
                final var imagePath = new java.util.concurrent.atomic.AtomicReference<String>();
                final boolean handled =
                        ClipboardImagePaster.paste(
                                imagePath::set,
                                message ->
                                        dialogService.errorMessage(
                                                message, "Clipboard image paste failed"));
                return handled ? imagePath.get() : delegate.getContents(useSystemSelection);
            }
        };
    }
}
