package com.jagent.desktop.services.persistence;

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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AppStatePersistence extends PersistenceSupport implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(AppStatePersistence.class);
    private static final long PERIOD_SECONDS = 1;

    private final AppState appState;
    private final Path directory;
    private final Path projectsFile;
    private final ScheduledExecutorService executor;
    private final ScheduledFuture<?> scheduledPersist;

    public AppStatePersistence(final AppState appState, final Path directory) {
        super();
        this.appState = appState;
        this.directory = directory;
        this.projectsFile = directory.resolve("projects.json");
        this.executor =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            final Thread thread = new Thread(runnable, "branchloom-persistence");
                            thread.setDaemon(true);
                            return thread;
                        });
        this.scheduledPersist =
                this.executor.scheduleWithFixedDelay(
                        this::persist, PERIOD_SECONDS, PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    public static AppState load(final Path directory) {
        return loadWithResult(directory).state();
    }

    public record LoadResult(AppState state, String settingsError) {
        public boolean hasSettingsError() {
            return settingsError != null;
        }
    }

    public static LoadResult loadWithResult(final Path directory) {
        final Path projectsFile = directory.resolve("projects.json");
        final Path settingsFile = directory.resolve("settings.json");
        final PersistedProjects projects =
                readOrDefault(projectsFile, PersistedProjects.class, new PersistedProjects());
        final SettingsResult settings = loadSettings(directory, settingsFile);
        return new LoadResult(
                new AppState(
                        settings.settings(),
                        projects.projects,
                        projects.sessions,
                        projects.terminals),
                settings.error());
    }

    public record SettingsResult(AppSettings settings, String error) {
        public boolean hasError() {
            return error != null;
        }
    }

    public static SettingsResult loadSettings(final Path directory) {
        return loadSettings(directory, directory.resolve("settings.json"));
    }

    public SettingsResult reloadSettings() {
        final SettingsResult result = loadSettings(directory);
        if (!result.hasError()) {
            appState.updateAppSettings(result.settings());
        }
        return result;
    }

    private static SettingsResult loadSettings(final Path directory, final Path settingsFile) {
        if (!Files.exists(settingsFile)) {
            final AppSettings defaults = logicalDefaults();
            try {
                Files.createDirectories(directory);
                writeInitialSettings(settingsFile, defaults);
                return new SettingsResult(defaults, null);
            } catch (IOException exception) {
                LOG.warn(
                        "Failed to initialize {}; using in-memory defaults.",
                        settingsFile,
                        exception);
                return new SettingsResult(
                        defaults,
                        "Branchloom could not create the initial settings file: "
                                + exception.getMessage());
            }
        }
        try {
            copySchema(settingsFile);
            return new SettingsResult(
                    read(settingsFile, AppSettings.class, Defaults.appSettings()), null);
        } catch (IOException | RuntimeException exception) {
            LOG.warn("Failed to load {}; using in-memory defaults.", settingsFile, exception);
            return new SettingsResult(
                    Defaults.appSettings(),
                    "Branchloom could not load settings.json at "
                            + settingsFile
                            + ". Fix the JSON and choose Refresh Settings.\n\n"
                            + exception.getMessage());
        }
    }

    private static AppSettings logicalDefaults() {
        final AppSettings defaults = Defaults.appSettings();
        return new AppSettings(
                AgentDetection.detect(),
                defaults.groupOrder(),
                defaults.reviewPrompt(),
                defaults.theme(),
                EditorDetection.detect(),
                defaults.worktreeTemplate(),
                defaults.agentContextPath(),
                defaults.pullRequestFilters(),
                defaults.boards(),
                defaults.jiraEndpoint());
    }

    private static void writeInitialSettings(final Path settingsFile, final AppSettings settings)
            throws IOException {
        final com.google.gson.JsonObject json = JSON.toJsonTree(settings).getAsJsonObject();
        json.addProperty(
                "$schema", settingsFile.resolveSibling("settings.schema.json").toUri().toString());
        Files.writeString(settingsFile, JSON.toJson(json));
        copySchema(settingsFile);
    }

    private static void copySchema(final Path settingsFile) throws IOException {
        final var schema = AppStatePersistence.class.getResourceAsStream("/settings.schema.json");
        if (schema != null) {
            try (schema) {
                Files.copy(
                        schema,
                        settingsFile.resolveSibling("settings.schema.json"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public void persist() {
        final AppState.PersistenceSnapshot snapshot;
        try {
            snapshot = snapshotOnEdt();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        } catch (RuntimeException exception) {
            LOG.warn("Failed to snapshot application state", exception);
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
        } catch (IOException exception) {
            LOG.warn("Failed to persist application state", exception);
            SwingUtilities.invokeLater(
                    () ->
                            appState.restorePersistenceUpdates(
                                    snapshot.projectsUpdated(), snapshot.terminalEvents()));
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
            LOG.warn("Failed to load {}; using defaults.", path, exception);
            return fallback;
        }
    }

    @Override
    public void close() {
        scheduledPersist.cancel(false);
        if (SwingUtilities.isEventDispatchThread()) {
            executor.shutdownNow();
            final AppState.PersistenceSnapshot snapshot = snapshotOnEdtUnchecked();
            if (snapshot != null) {
                write(snapshot);
            }
            return;
        }
        persist();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warn("Application state persistence did not finish before shutdown.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while closing application state persistence", exception);
        }
    }

    private AppState.PersistenceSnapshot snapshotOnEdtUnchecked() {
        try {
            return snapshotOnEdt();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException exception) {
            LOG.warn("Failed to snapshot application state", exception);
            return null;
        }
    }

    private static final class PersistedProjects {
        private final Map<String, Project> projects = new LinkedHashMap<>();
        private final Map<String, Session> sessions = new LinkedHashMap<>();
        private final Map<String, Terminal> terminals = new LinkedHashMap<>();
    }
}
