package com.jagent.desktop.services.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jagent.desktop.models.AppSettings;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.AgentDetection;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.EditorDetection;
import com.jagent.desktop.ui.Defaults;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

public final class AppStatePersistence implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(AppStatePersistence.class.getName());
    private static final Path DIRECTORY = Path.of(System.getProperty("user.home"), ".branchloom");
    private static final Path PROJECTS_FILE = DIRECTORY.resolve("projects.json");
    private static final Path SETTINGS_FILE = DIRECTORY.resolve("settings.json");
    private static final long PERIOD_SECONDS = 1;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    private final AppState appState;
    private final ScheduledExecutorService executor;

    public AppStatePersistence(final AppState appState) {
        this.appState = appState;
        this.executor =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            final Thread thread = new Thread(runnable, "branchloom-persistence");
                            thread.setDaemon(true);
                            return thread;
                        });
        this.executor.scheduleWithFixedDelay(
                this::persist, PERIOD_SECONDS, PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    public static AppState load() {
        try {
            final boolean settingsExist = Files.exists(SETTINGS_FILE);
            final PersistedProjects projects =
                    read(PROJECTS_FILE, PersistedProjects.class, new PersistedProjects());
            final AppSettings loadedSettings =
                    read(SETTINGS_FILE, AppSettings.class, Defaults.appSettings());
            final AppSettings settings = settings(settingsExist, loadedSettings);
            return new AppState(settings, projects.projects, projects.sessions, projects.terminals);
        } catch (IOException | RuntimeException exception) {
            LOG.log(Level.WARNING, "Failed to load application state; using defaults.", exception);
            return new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        }
    }

    private static AppSettings settings(
            final boolean settingsExist, final AppSettings loadedSettings) {
        if (settingsExist || loadedSettings == null) {
            return loadedSettings == null ? Defaults.appSettings() : loadedSettings;
        }
        final AppSettings defaults = Defaults.appSettings();
        return new AppSettings(
                AgentDetection.detect(),
                defaults.groupOrder(),
                defaults.reviewPrompt(),
                defaults.theme(),
                EditorDetection.detect(),
                defaults.worktreeTemplate());
    }

    public void persist() {
        final AppState.PersistenceSnapshot snapshot;
        try {
            snapshot = snapshotOnEdt();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        } catch (RuntimeException exception) {
            LOG.log(Level.WARNING, "Failed to snapshot application state", exception);
            return;
        }
        if (snapshot == null) {
            return;
        }
        executor.execute(() -> write(snapshot));
    }

    private AppState.PersistenceSnapshot snapshotOnEdt() throws InterruptedException {
        if (SwingUtilities.isEventDispatchThread()) {
            return appState.snapshotForPersistence();
        }
        final AtomicReference<AppState.PersistenceSnapshot> snapshot = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> snapshot.set(appState.snapshotForPersistence()));
        } catch (java.lang.reflect.InvocationTargetException exception) {
            throw new IllegalStateException(
                    "Failed to snapshot application state", exception.getCause());
        }
        return snapshot.get();
    }

    private void write(final AppState.PersistenceSnapshot snapshot) {
        try {
            Files.createDirectories(DIRECTORY);
            if (snapshot.projectsUpdated()) {
                final PersistedProjects projects = new PersistedProjects();
                snapshot.projects()
                        .forEach(
                                (id, project) ->
                                        projects.projects.put(id.value().toString(), project));
                snapshot.sessions()
                        .forEach(
                                (id, session) ->
                                        projects.sessions.put(id.value().toString(), session));
                snapshot.terminals()
                        .forEach(
                                (id, terminal) ->
                                        projects.terminals.put(id.value().toString(), terminal));
                writeAtomically(PROJECTS_FILE, projects);
                snapshot.terminalEvents().forEach(this::updateTerminalHistory);
            }
            if (snapshot.appSettingsUpdated()) {
                writeAtomically(SETTINGS_FILE, snapshot.appSettings());
            }
        } catch (IOException exception) {
            LOG.log(Level.WARNING, "Failed to persist application state", exception);
            SwingUtilities.invokeLater(
                    () ->
                            appState.restorePersistenceUpdates(
                                    snapshot.appSettingsUpdated(),
                                    snapshot.projectsUpdated(),
                                    snapshot.terminalEvents()));
        }
    }

    private void updateTerminalHistory(final AppState.TerminalEvent event) {
        final Path history = Path.of(historyFile(event.terminalId()));
        try {
            if (event.action() == AppState.TerminalAction.ADD) {
                TerminalHistory.ensureParent(history.toString());
                Files.createFile(history);
            } else {
                TerminalHistory.delete(history.toString());
            }
        } catch (IOException ignored) {
            // History maintenance should not prevent state persistence.
        }
    }

    private static String historyFile(final TerminalId terminalId) {
        return Path.of(
                        System.getProperty("user.home"),
                        ".branchloom",
                        "terminal-history",
                        terminalId.value() + ".history")
                .toString();
    }

    private static void writeAtomically(final Path path, final Object value) throws IOException {
        final Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, JSON.toJson(value));
        Files.move(
                temporary,
                path,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private static <T> T read(final Path path, final Class<T> type, final T fallback)
            throws IOException {
        if (!Files.exists(path)) {
            return fallback;
        }
        return JSON.fromJson(Files.readString(path), type);
    }

    @Override
    public void close() {
        persist();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warning("Application state persistence did not finish before shutdown.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOG.log(
                    Level.WARNING,
                    "Interrupted while closing application state persistence",
                    exception);
        }
    }

    private static final class PersistedProjects {
        private final Map<String, Project> projects = new LinkedHashMap<>();
        private final Map<String, Session> sessions = new LinkedHashMap<>();
        private final Map<String, Terminal> terminals = new LinkedHashMap<>();
    }
}
