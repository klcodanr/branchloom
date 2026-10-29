package com.jagent.desktop.ui.components.terminal;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.TerminalResources;
import com.jagent.desktop.services.terminal.TerminalManager;
import com.jagent.desktop.services.terminal.TerminalRuntime;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiConstants;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.JediTermWidget;
import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
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

    public TerminalPanel(
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

    public void focusTerminal() {
        SwingUtilities.invokeLater(terminal::requestFocusInWindow);
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

    public static void pruneRetainedPanels(final Set<TerminalId> terminalIds) {
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
                    final JLabel failure =
                            new JLabel("Could not start terminal: " + exception.getMessage());
                    failure.setFont(Theme.font(Theme.FontSize.SM));
                    add(failure, BorderLayout.NORTH);
                    revalidate();
                    repaint();
                });
    }
}
