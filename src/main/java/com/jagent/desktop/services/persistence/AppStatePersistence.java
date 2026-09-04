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
import java.lang.reflect.InvocationTargetException;
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
    private static final Path DEFAULT_DIRECTORY =
            Path.of(System.getProperty("user.home"), ".branchloom");
    private static final long PERIOD_SECONDS = 1;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    private final AppState appState;
    private final Path directory;
    private final Path projectsFile;
    private final Path settingsFile;
    private final ScheduledExecutorService executor;

    public AppStatePersistence(final AppState appState) {
        this(appState, DEFAULT_DIRECTORY);
    }

    public AppStatePersistence(final AppState appState, final Path directory) {
        this.appState = appState;
        this.directory = directory;
        this.projectsFile = directory.resolve("projects.json");
        this.settingsFile = directory.resolve("settings.json");
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
        return load(DEFAULT_DIRECTORY);
    }

    public static AppState load(final Path directory) {
        final Path projectsFile = directory.resolve("projects.json");
        final Path settingsFile = directory.resolve("settings.json");
        final boolean settingsExist = Files.exists(settingsFile);
        final PersistedProjects projects =
                readOrDefault(projectsFile, PersistedProjects.class, new PersistedProjects());
        final AppSettings loadedSettings =
                readOrDefault(settingsFile, AppSettings.class, Defaults.appSettings());
        final AppSettings settings = settings(settingsExist, loadedSettings);
        return new AppState(settings, projects.projects, projects.sessions, projects.terminals);
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
                defaults.worktreeTemplate(),
                defaults.reviewPlanEnabled(),
                defaults.reviewPlanCommand(),
                defaults.reviewPlanPrompt());
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
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException(
                    "Failed to snapshot application state", exception.getCause());
        }
        return snapshot.get();
    }

    private void write(final AppState.PersistenceSnapshot snapshot) {
        try {
            Files.createDirectories(directory);
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
                writeAtomically(projectsFile, projects);
                snapshot.terminalEvents().forEach(this::updateTerminalHistory);
            }
            if (snapshot.appSettingsUpdated()) {
                writeAtomically(settingsFile, snapshot.appSettings());
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

    private String historyFile(final TerminalId terminalId) {
        return directory
                .resolve("terminal-history")
                .resolve(terminalId.value() + ".history")
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

    private static <T> T readOrDefault(final Path path, final Class<T> type, final T fallback) {
        try {
            return read(path, type, fallback);
        } catch (IOException | RuntimeException exception) {
            LOG.log(Level.WARNING, "Failed to load " + path + "; using defaults.", exception);
            return fallback;
        }
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
