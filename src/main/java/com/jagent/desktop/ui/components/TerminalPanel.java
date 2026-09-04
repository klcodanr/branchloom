package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.terminal.TerminalManager;
import com.jagent.desktop.services.terminal.TerminalRuntime;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jediterm.core.Color;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.emulator.ColorPaletteImpl;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import org.jetbrains.annotations.Nullable;

/** UI attachment for a managed terminal runtime. */
public final class TerminalPanel extends JPanel {
    private static final ConcurrentMap<TerminalId, TerminalPanel> RETAINED_PANELS =
            new ConcurrentHashMap<>();
    private final transient TerminalManager manager = TerminalManager.get();
    private final transient TerminalRuntime runtime;
    private final transient @Nullable TerminalId retainedId;
    private final JediTermWidget terminal;

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
        super(new BorderLayout());
        runtime = manager.create(command, directory, resourceName);
        retainedId = null;
        setOpaque(false);
        setBorder(new EmptyBorder(14, 14, 14, 14));
        terminal = new AppJediTermWidget(80, 24, new AppTerminalSettings());
        add(terminal, BorderLayout.CENTER);
        setStateChanged(stateChanged);
    }

    private TerminalPanel(final TerminalId retainedId, final TerminalRuntime runtime) {
        super(new BorderLayout());
        this.retainedId = retainedId;
        this.runtime = runtime;
        setOpaque(false);
        setBorder(new EmptyBorder(14, 14, 14, 14));
        terminal = new AppJediTermWidget(80, 24, new AppTerminalSettings());
        add(terminal, BorderLayout.CENTER);
        setStateChanged(ignored -> {});
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

    private void attach(final TtyConnector connector) {
        SwingUtilities.invokeLater(
                () -> {
                    terminal.setTtyConnector(connector);
                    terminal.start();
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
        private static final ColorPalette PALETTE =
                new ColorPalette() {
                    @Override
                    protected Color getForegroundByColorIndex(final int index) {
                        return index == 7
                                ? toTerminalColor(UIManager.getColor("Label.foreground"))
                                : ColorPaletteImpl.XTERM_PALETTE.getForeground(
                                        TerminalColor.index(index));
                    }

                    @Override
                    protected Color getBackgroundByColorIndex(final int index) {
                        return index == 0
                                ? toTerminalColor(UIManager.getColor("Panel.background"))
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
            return terminalColor(UIManager.getColor("Label.foreground"));
        }

        @Override
        public TerminalColor getDefaultBackground() {
            return terminalColor(UIManager.getColor("Panel.background"));
        }

        @Override
        public TextStyle getSelectionColor() {
            return new TextStyle(
                    terminalColor(UIManager.getColor("Label.foreground")),
                    terminalColor(UIManager.getColor("Component.focusColor")));
        }
    }

    private static final class AppJediTermWidget extends JediTermWidget {
        private AppJediTermWidget(
                final int columns, final int rows, final AppTerminalSettings settings) {
            super(columns, rows, settings);
            getTerminalPanel()
                    .addCustomKeyListener(
                            new KeyAdapter() {
                                @Override
                                public void keyPressed(final KeyEvent event) {
                                    if (event.getKeyCode() == KeyEvent.VK_ENTER
                                            && event.isControlDown()) {
                                        final var starter = getTerminalStarter();
                                        if (starter != null) {
                                            starter.sendBytes(new byte[] {0x0A}, true);
                                            event.consume();
                                        }
                                    }
                                }
                            });
        }

        @Override
        protected JScrollBar createScrollBar() {
            final JScrollBar bar = new JScrollBar();
            bar.setUnitIncrement(16);
            return bar;
        }
    }

    private static TerminalColor terminalColor(final java.awt.Color color) {
        return TerminalColor.rgb(color.getRed(), color.getGreen(), color.getBlue());
    }

    private static Color toTerminalColor(final java.awt.Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue());
    }
}
