package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.terminal.TerminalManager;
import com.jagent.desktop.services.terminal.TerminalRuntime;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jagent.desktop.ui.actions.CopyPathAction;
import com.jagent.desktop.ui.utils.ClipboardImagePaster;
import com.jediterm.core.Color;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TerminalCopyPasteHandler;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.emulator.ColorPaletteImpl;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import javax.swing.BoundedRangeModel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import org.jetbrains.annotations.Nullable;

/** UI attachment for a managed terminal runtime. */
public final class TerminalPanel extends JPanel {
    private static final ConcurrentMap<TerminalId, TerminalPanel> RETAINED_PANELS =
            new ConcurrentHashMap<>();
    private final transient TerminalManager manager = TerminalManager.get();
    private final transient TerminalRuntime runtime;
    private final transient @Nullable TerminalId retainedId;
    private final JediTermWidget terminal;
    private volatile Consumer<String> titleChanged = ignored -> {};

    public TerminalPanel(final String command, final Path directory) {
        this(command, directory, "", ignored -> {});
    }

    public TerminalPanel(
            final String command,
            final Path directory,
            final Consumer<TerminalState> stateChanged) {
        this(command, directory, "", stateChanged);
    }

    public TerminalPanel(final String command, final Path directory, final String resourceName) {
        this(command, directory, resourceName, ignored -> {});
    }

    public TerminalPanel(
            final String command,
            final Path directory,
            final String resourceName,
            final Consumer<TerminalState> stateChanged) {
        this(TerminalManager.get().create(command, directory, resourceName), null, stateChanged);
    }

    protected TerminalPanel(
            final TerminalRuntime runtime, final Consumer<TerminalState> stateChanged) {
        this(runtime, null, stateChanged);
    }

    private TerminalPanel(
            final TerminalRuntime runtime,
            final @Nullable TerminalId retainedId,
            final Consumer<TerminalState> stateChanged) {
        super(new BorderLayout());
        this.runtime = runtime;
        this.retainedId = retainedId;
        setOpaque(false);
        setBorder(
                new javax.swing.border.EmptyBorder(
                        UiConstants.CARD_PADDING, 0, UiConstants.CARD_PADDING, 0));
        terminal =
                new AppJediTermWidget(
                        80,
                        24,
                        new AppTerminalSettings(),
                        runtime.directory(),
                        this,
                        this::applicationTitleChanged);
        add(terminal, BorderLayout.CENTER);
        setStateChanged(stateChanged);
    }

    private TerminalPanel(final TerminalId retainedId, final TerminalRuntime runtime) {
        this(runtime, retainedId, ignored -> {});
    }

    public static TerminalPanel retained(
            final TerminalId id,
            final Terminal definition,
            final Path directory,
            final String resourceName) {
        return RETAINED_PANELS.computeIfAbsent(
                id,
                ignored ->
                        new TerminalPanel(
                                id,
                                TerminalManager.get()
                                        .retained(id, definition, directory, resourceName)));
    }

    public static TerminalPanel existing(final TerminalId id) {
        return RETAINED_PANELS.get(id);
    }

    public void start() {
        runtime.start(this::attach, this::showStartupFailure);
    }

    public void setStateChanged(final Consumer<TerminalState> listener) {
        final Consumer<TerminalState> callback = listener == null ? ignored -> {} : listener;
        runtime.onStateChanged(state -> SwingUtilities.invokeLater(() -> callback.accept(state)));
    }

    public TerminalState state() {
        return runtime.state();
    }

    public static TerminalState state(final TerminalId id) {
        return TerminalManager.get().state(id);
    }

    public static void reconcile(final Set<TerminalId> terminalIds) {
        TerminalManager.get().reconcile(terminalIds);
        RETAINED_PANELS
                .entrySet()
                .removeIf(
                        entry -> {
                            if (terminalIds.contains(entry.getKey())) {
                                return false;
                            }
                            entry.getValue().terminal.close();
                            return true;
                        });
    }

    public void dispose() {
        if (retainedId != null) {
            RETAINED_PANELS.remove(retainedId, this);
            manager.dispose(retainedId, runtime, true);
        } else {
            manager.dispose(runtime, true);
        }
        terminal.close();
    }

    public void setResourceName(final String resourceName) {
        manager.setResourceName(runtime, resourceName);
    }

    public void setTitleChanged(final Consumer<String> listener) {
        titleChanged = listener == null ? ignored -> {} : listener;
    }

    /* package */ void applicationTitleChanged(final String title) {
        if (title == null) {
            return;
        }
        final String updatedTitle = title.trim();
        if (updatedTitle.isEmpty()) {
            return;
        }
        SwingUtilities.invokeLater(() -> titleChanged.accept(updatedTitle));
    }

    private void attach(final TtyConnector connector) {
        SwingUtilities.invokeLater(
                () -> {
                    terminal.setTtyConnector(connector);
                    terminal.start();
                    BackgroundTasks.submit(
                            "Terminals", "agent-terminal-submit-command", runtime::submitCommand);
                });
    }

    private void showStartupFailure(final Exception exception) {
        SwingUtilities.invokeLater(
                () -> {
                    removeAll();
                    add(
                            UiFactory.label(
                                    "Could not start terminal: " + exception.getMessage(),
                                    Theme.FontSize.SM),
                            BorderLayout.NORTH);
                    revalidate();
                    repaint();
                });
    }

    private static final class AppTerminalSettings extends DefaultSettingsProvider {
        private static final String FOCUS_COLOR = "Component.focusColor";
        private static final String LABEL_FOREGROUND = "Label.foreground";
        private static final String PANEL_BACKGROUND = "Panel.background";
        private static final ColorPalette PALETTE =
                new ColorPalette() {
                    @Override
                    protected Color getForegroundByColorIndex(final int index) {
                        return index == 7
                                ? toTerminalColor(UIManager.getColor(LABEL_FOREGROUND))
                                : ColorPaletteImpl.XTERM_PALETTE.getForeground(
                                        TerminalColor.index(index));
                    }

                    @Override
                    protected Color getBackgroundByColorIndex(final int index) {
                        return index == 0
                                ? toTerminalColor(UIManager.getColor(PANEL_BACKGROUND))
                                : ColorPaletteImpl.XTERM_PALETTE.getBackground(
                                        TerminalColor.index(index));
                    }
                };

        @Override
        public ColorPalette getTerminalColorPalette() {
            return PALETTE;
        }

        @Override
        public Font getTerminalFont() {
            return Theme.terminalFont(Theme.FontSize.SM);
        }

        @Override
        public float getTerminalFontSize() {
            return Theme.FontSize.SM.points();
        }

        @Override
        public TerminalColor getDefaultForeground() {
            return terminalColor(UIManager.getColor(LABEL_FOREGROUND));
        }

        @Override
        public TerminalColor getDefaultBackground() {
            return terminalColor(UIManager.getColor(PANEL_BACKGROUND));
        }

        @Override
        public TextStyle getSelectionColor() {
            return new TextStyle(
                    terminalColor(UIManager.getColor(LABEL_FOREGROUND)),
                    terminalColor(UIManager.getColor(FOCUS_COLOR)));
        }

        @Override
        public TextStyle getHyperlinkColor() {
            final var focusColor = UIManager.getColor(FOCUS_COLOR);
            return new TextStyle(
                    terminalColor(
                            focusColor == null ? UIManager.getColor(LABEL_FOREGROUND) : focusColor),
                    terminalColor(UIManager.getColor(PANEL_BACKGROUND)));
        }
    }

    private static final class AppJediTermWidget extends JediTermWidget {
        private boolean linkActivationAllowed;

        private AppJediTermWidget(
                final int columns,
                final int rows,
                final AppTerminalSettings settings,
                final Path directory,
                final JPanel owner,
                final Consumer<String> titleChanged) {
            super(columns, rows, settings);
            myTerminal.addApplicationTitleListener(titleChanged::accept);
            addHyperlinkFilter(
                    new TerminalLinkFilter(
                            PlatformCommands::openUrl, this::isLinkActivationAllowed));
            addHyperlinkFilter(
                    new TerminalFileLinkFilter(
                            directory,
                            link -> TerminalFileLinkOpener.open(link, directory, owner),
                            this::isLinkActivationAllowed));
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

        private boolean isLinkActivationAllowed() {
            return linkActivationAllowed;
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
                    settings, textBuffer, styleState, allowed -> linkActivationAllowed = allowed);
        }
    }

    protected static boolean isLiteralNewlineShortcut(final KeyEvent event) {
        return event.getKeyCode() == KeyEvent.VK_ENTER
                && (event.isControlDown() || event.isShiftDown());
    }

    private static final class AppTerminalPanel extends com.jediterm.terminal.ui.TerminalPanel {
        private transient TerminalStarter terminalStarter;
        private transient Point lastMousePoint;
        private final transient Consumer<Boolean> linkActivationChanged;

        private AppTerminalPanel(
                final com.jediterm.terminal.ui.settings.SettingsProvider settings,
                final TerminalTextBuffer textBuffer,
                final StyleState styleState,
                final Consumer<Boolean> linkActivationChanged) {
            super(settings, textBuffer, styleState);
            this.linkActivationChanged = linkActivationChanged;
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
                                if (!(files.get(0) instanceof File file)) {
                                    return false;
                                }
                                terminalStarter.sendString(
                                        file.toPath().toAbsolutePath().normalize().toString(),
                                        true);
                                return true;
                            } catch (IOException
                                    | java.awt.datatransfer.UnsupportedFlavorException ignored) {
                                return false;
                            }
                        }
                    });
        }

        @Override
        protected void processMouseEvent(final java.awt.event.MouseEvent event) {
            lastMousePoint = event.getPoint();
            linkActivationChanged.accept(
                    event.getID() == java.awt.event.MouseEvent.MOUSE_CLICKED
                            && event.getButton() == java.awt.event.MouseEvent.BUTTON1
                            && (event.isControlDown() || event.isMetaDown()));
            super.processMouseEvent(event);
            linkActivationChanged.accept(false);
        }

        @Override
        protected JPopupMenu createPopupMenu(
                final com.jediterm.terminal.ui.TerminalActionProvider actionProvider) {
            final JPopupMenu menu = super.createPopupMenu(actionProvider);
            final String link = linkAt(lastMousePoint);
            if (link != null) {
                final JMenuItem copyLink = new JMenuItem("Copy Link");
                copyLink.addActionListener(ignored -> CopyPathAction.copy(link));
                menu.addSeparator();
                menu.add(copyLink);
            }
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
            if (!(style instanceof com.jediterm.terminal.HyperlinkStyle hyperlink)) {
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
                                            JOptionPane.showMessageDialog(
                                                    AppTerminalPanel.this,
                                                    message,
                                                    "Clipboard image paste failed",
                                                    JOptionPane.ERROR_MESSAGE));
                    return handled ? imagePath.get() : delegate.getContents(useSystemSelection);
                }
            };
        }
    }

    private static final class AppScrollBar extends JScrollBar {
        @Override
        public Dimension getPreferredSize() {
            final BoundedRangeModel model = getModel();
            if (model.getMaximum() - model.getMinimum() <= model.getExtent()) {
                return new Dimension(0, 0);
            }
            return super.getPreferredSize();
        }

        @Override
        public void setModel(final BoundedRangeModel model) {
            super.setModel(model);
            model.addChangeListener(
                    ignored -> {
                        revalidate();
                        if (getParent() != null) {
                            getParent().revalidate();
                        }
                    });
        }
    }

    private static TerminalColor terminalColor(final java.awt.Color color) {
        return TerminalColor.rgb(color.getRed(), color.getGreen(), color.getBlue());
    }

    private static Color toTerminalColor(final java.awt.Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue());
    }
}
