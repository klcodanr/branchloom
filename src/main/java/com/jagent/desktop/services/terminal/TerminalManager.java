package com.jagent.desktop.services.terminal;

import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.TerminalResources;
import com.jagent.desktop.services.persistence.TerminalHistory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Application-wide owner of retained terminal runtimes. */
public final class TerminalManager {
    private static final TerminalManager INSTANCE = new TerminalManager();
    private final ConcurrentMap<TerminalId, TerminalRuntime> retained = new ConcurrentHashMap<>();
    private final ConcurrentMap<TerminalRuntime, String> resources = new ConcurrentHashMap<>();

    private TerminalManager() {}

    public static TerminalManager get() {
        return INSTANCE;
    }

    public TerminalRuntime retained(
            final TerminalId id,
            final Terminal terminal,
            final Path directory,
            final String resourceName) {
        final TerminalRuntime runtime =
                retained.computeIfAbsent(
                        id, ignored -> create(terminal.command(), directory, resourceName));
        resources.put(runtime, resourceName);
        return runtime;
    }

    public TerminalRuntime create(
            final String command, final Path directory, final String resourceName) {
        try {
            final TerminalRuntime runtime =
                    new TerminalRuntime(command, directory, TerminalHistory.createPath());
            resources.put(runtime, resourceName);
            return runtime;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create terminal history file", exception);
        }
    }

    public TerminalState state(final TerminalId id) {
        final TerminalRuntime runtime = retained.get(id);
        return runtime == null ? null : runtime.state();
    }

    public void dispose(
            final TerminalId id, final TerminalRuntime runtime, final boolean deleteHistory) {
        retained.remove(id, runtime);
        dispose(runtime, deleteHistory);
    }

    public void dispose(final TerminalRuntime runtime, final boolean deleteHistory) {
        resources.remove(runtime);
        runtime.stop();
        if (deleteHistory) {
            TerminalHistory.delete(runtime.historyFile());
        }
        retained.values().removeIf(value -> value.equals(runtime));
    }

    /** Stops every terminal, including runtimes that are not retained in application state. */
    public void disposeAll() {
        final Set<TerminalRuntime> runtimes = new HashSet<>(resources.keySet());
        runtimes.addAll(retained.values());
        resources.clear();
        retained.clear();
        runtimes.forEach(TerminalRuntime::stopAndWait);
    }

    public void setResourceName(final TerminalRuntime runtime, final String resourceName) {
        resources.put(runtime, resourceName);
    }

    public void reconcile(final Set<TerminalId> terminalIds) {
        for (final var entry : retained.entrySet()) {
            if (!terminalIds.contains(entry.getKey())) {
                dispose(entry.getKey(), entry.getValue(), true);
            }
        }
    }

    public List<TerminalResources.ProcessTarget> activeProcesses() {
        return resources.entrySet().stream()
                .filter(
                        entry ->
                                entry.getKey().process() != null
                                        && entry.getKey().process().isAlive())
                .map(
                        entry ->
                                new TerminalResources.ProcessTarget(
                                        entry.getValue(), entry.getKey().process().pid()))
                .toList();
    }
}
