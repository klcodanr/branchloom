package com.jagent.desktop.ui.components.terminal;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.TerminalResources;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.terminal.TerminalManager;
import com.jagent.desktop.services.terminal.TerminalRuntime;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiConstants;
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

    private TerminalPanel(
            final TerminalRuntime runtime,
            final @Nullable TerminalId retainedId,
            final Consumer<TerminalState> stateChanged,
            final DialogService dialogService) {
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
                        this::applicationTitleChanged,
                        dialogService);
        add(terminal, BorderLayout.CENTER);
        setStateChanged(stateChanged);
    }

    public static TerminalPanel retained(
            final TerminalId id,
            final Terminal definition,
            final Path directory,
            final String resourceName,
            final DialogService dialogService) {
        return RETAINED_PANELS.computeIfAbsent(
                id,
                ignored ->
                        new TerminalPanel(
                                TerminalManager.get()
                                        .retained(id, definition, directory, resourceName),
                                id,
                                null,
                                dialogService));
    }

    public static TerminalPanel existing(final TerminalId id) {
        return RETAINED_PANELS.get(id);
    }

    public void start() {
        runtime.start(
                (connector) ->
                        SwingUtilities.invokeLater(
                                () -> {
                                    terminal.setTtyConnector(connector);
                                    terminal.start();
                                    BackgroundOperations.submit(
                                            "Terminals",
                                            "agent-terminal-submit-command",
                                            Executors.callable(runtime::submitCommand));
                                }),
                (exception) ->
                        SwingUtilities.invokeLater(
                                () -> {
                                    removeAll();
                                    final JLabel failure =
                                            new JLabel(
                                                    "Could not start terminal: "
                                                            + exception.getMessage());
                                    failure.setFont(Theme.font(Theme.FontSize.SM));
                                    add(failure, BorderLayout.NORTH);
                                    revalidate();
                                    repaint();
                                }));
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

    private void applicationTitleChanged(final String title) {
        if (title == null || title.isEmpty()) {
            return;
        }
        SwingUtilities.invokeLater(() -> titleChanged.accept(title.trim()));
    }
}
