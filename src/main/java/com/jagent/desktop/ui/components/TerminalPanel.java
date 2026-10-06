package com.jagent.desktop.ui.components;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.TerminalResources;
import com.jagent.desktop.services.terminal.TerminalManager;
import com.jagent.desktop.services.terminal.TerminalRuntime;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jagent.desktop.ui.utils.ClipboardImagePaster;
import com.jediterm.core.Color;
import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.HyperlinkStyle;
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
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.BoundedRangeModel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
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

    public boolean hasRunningChildProcess() {
        final var process = runtime.process();
        return process != null
                && process.isAlive()
                && TerminalResources.hasLiveDescendant(process.pid());
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
                    BackgroundOperations.submit(
                            "Terminals",
                            "agent-terminal-submit-command",
                            Executors.callable(runtime::submitCommand));
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

    protected static final class AppTerminalSettings extends DefaultSettingsProvider {
        private static final ColorPalette PALETTE =
                new ColorPalette() {
                    @Override
                    protected Color getForegroundByColorIndex(final int index) {
                        return terminalColorByIndex(index, false);
                    }

                    @Override
                    protected Color getBackgroundByColorIndex(final int index) {
                        return terminalColorByIndex(index, true);
                    }

                    private Color terminalColorByIndex(final int index, final boolean background) {
                        final java.awt.Color defaultColor =
                                background
                                        ? fromTerminalColor(
                                                ColorPaletteImpl.XTERM_PALETTE.getBackground(
                                                        TerminalColor.index(index)))
                                        : fromTerminalColor(
                                                ColorPaletteImpl.XTERM_PALETTE.getForeground(
                                                        TerminalColor.index(index)));
                        return toTerminalColor(ansiColor(index, defaultColor, background));
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
            return terminalColor(Theme.Colors.foreground());
        }

        @Override
        public TerminalColor getDefaultBackground() {
            return terminalColor(Theme.Colors.background());
        }

        @Override
        public TextStyle getDefaultStyle() {
            return new TextStyle(getDefaultForeground(), getDefaultBackground());
        }

        @Override
        public TextStyle getSelectionColor() {
            final java.awt.Color selectionBackground = Theme.Colors.focus();
            return new TextStyle(
                    terminalColor(contrastingForeground(selectionBackground)),
                    terminalColor(selectionBackground));
        }

        @Override
        public boolean useInverseSelectionColor() {
            return false;
        }

        @Override
        public TextStyle getFoundPatternColor() {
            final java.awt.Color foundPatternBackground = Theme.Colors.warning();
            return new TextStyle(
                    terminalColor(contrastingForeground(foundPatternBackground)),
                    terminalColor(foundPatternBackground));
        }

        @Override
        public TextStyle getHyperlinkColor() {
            return new TextStyle(
                    terminalColor(Theme.Colors.focus()), terminalColor(Theme.Colors.background()));
        }

        @Override
        public HyperlinkStyle.HighlightMode getHyperlinkHighlightingMode() {
            return HyperlinkStyle.HighlightMode.ALWAYS;
        }

        @Override
        public int caretBlinkingMs() {
            return 650;
        }

        private static java.awt.Color ansiColor(
                final int index, final java.awt.Color fallback, final boolean background) {
            final java.awt.Color panelBackground = Theme.Colors.background();
            final java.awt.Color labelForeground = Theme.Colors.foreground();
            final java.awt.Color disabledForeground = Theme.Colors.muted();

            final java.awt.Color blue = Theme.Colors.focus();
            final java.awt.Color magenta = Theme.Colors.merge();
            final java.awt.Color cyan = blend(Theme.Colors.success(), blue, 0.45f);
            final java.awt.Color black = blend(panelBackground, labelForeground, 0.22f);
            final java.awt.Color white = labelForeground;
            final java.awt.Color[] ansi = {
                background ? panelBackground : black,
                Theme.Colors.danger(),
                Theme.Colors.success(),
                Theme.Colors.warning(),
                blue,
                magenta,
                cyan,
                white,
                disabledForeground,
                lift(Theme.Colors.danger(), 0.22f),
                lift(Theme.Colors.success(), 0.22f),
                lift(Theme.Colors.warning(), 0.12f),
                lift(blue, 0.22f),
                lift(magenta, 0.18f),
                lift(cyan, 0.16f),
                lift(white, 0.08f)
            };
            if (index < 0 || index >= ansi.length) {
                return fallback;
            }
            return ansi[index];
        }

        private static java.awt.Color contrastingForeground(final java.awt.Color background) {
            final java.awt.Color panelBackground = Theme.Colors.background();
            final java.awt.Color labelForeground = Theme.Colors.foreground();
            final double labelContrast = contrastRatio(background, labelForeground);
            final double panelContrast = contrastRatio(background, panelBackground);
            return labelContrast >= panelContrast ? labelForeground : panelBackground;
        }

        private static java.awt.Color blend(
                final java.awt.Color first, final java.awt.Color second, final float ratio) {
            final float clampedRatio = Math.max(0f, Math.min(1f, ratio));
            final float inverseRatio = 1f - clampedRatio;
            return new java.awt.Color(
                    Math.round(first.getRed() * inverseRatio + second.getRed() * clampedRatio),
                    Math.round(first.getGreen() * inverseRatio + second.getGreen() * clampedRatio),
                    Math.round(first.getBlue() * inverseRatio + second.getBlue() * clampedRatio));
        }

        private static java.awt.Color lift(final java.awt.Color color, final float amount) {
            return blend(color, java.awt.Color.WHITE, amount);
        }

        private static double contrastRatio(
                final java.awt.Color background, final java.awt.Color foreground) {
            final double backgroundLuminance = relativeLuminance(background);
            final double foregroundLuminance = relativeLuminance(foreground);
            final double lighter = Math.max(backgroundLuminance, foregroundLuminance);
            final double darker = Math.min(backgroundLuminance, foregroundLuminance);
            return (lighter + 0.05d) / (darker + 0.05d);
        }

        private static double relativeLuminance(final java.awt.Color color) {
            final double red = linearize(color.getRed() / 255d);
            final double green = linearize(color.getGreen() / 255d);
            final double blue = linearize(color.getBlue() / 255d);
            return 0.2126d * red + 0.7152d * green + 0.0722d * blue;
        }

        private static double linearize(final double component) {
            if (component <= 0.039_28d) {
                return component / 12.92d;
            }
            return Math.pow((component + 0.055d) / 1.055d, 2.4d);
        }
    }

    private static final class AppJediTermWidget extends JediTermWidget {
        private boolean modifiedClickLinkActivationAllowed;
        private boolean contextMenuLinkActivationAllowed;

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
    }

    protected static boolean isLiteralNewlineShortcut(final KeyEvent event) {
        return event.getKeyCode() == KeyEvent.VK_ENTER
                && (event.isControlDown() || event.isShiftDown());
    }

    protected static boolean isModifiedLinkActivationEvent(final java.awt.event.MouseEvent event) {
        return event.getID() == java.awt.event.MouseEvent.MOUSE_CLICKED
                && event.getButton() == java.awt.event.MouseEvent.BUTTON1
                && (event.isControlDown() || event.isMetaDown());
    }

    private static final class AppTerminalPanel extends com.jediterm.terminal.ui.TerminalPanel {
        private transient TerminalStarter terminalStarter;
        private transient Point lastMousePoint;
        private final transient Consumer<Boolean> modifiedClickLinkActivationChanged;
        private final transient Consumer<Boolean> contextMenuLinkActivationChanged;

        private AppTerminalPanel(
                final com.jediterm.terminal.ui.settings.SettingsProvider settings,
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
            if (link != null) {
                menu.addPopupMenuListener(
                        new PopupMenuListener() {
                            @Override
                            public void popupMenuWillBecomeVisible(final PopupMenuEvent event) {
                                contextMenuLinkActivationChanged.accept(true);
                            }

                            @Override
                            public void popupMenuWillBecomeInvisible(final PopupMenuEvent event) {
                                contextMenuLinkActivationChanged.accept(false);
                            }

                            @Override
                            public void popupMenuCanceled(final PopupMenuEvent event) {
                                contextMenuLinkActivationChanged.accept(false);
                            }
                        });
            } else {
                contextMenuLinkActivationChanged.accept(false);
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

    private static java.awt.Color fromTerminalColor(final Color color) {
        return new java.awt.Color(color.getRed(), color.getGreen(), color.getBlue());
    }
}
