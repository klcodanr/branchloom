package com.jagent.desktop.services.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.AppSettings;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.ui.Defaults;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppStatePersistenceTest {
    private static final String SETTINGS_FILE = "settings.json";

    @Test
    void missingFilesLoadDefaultSettingsAndEmptyState(@TempDir final Path directory) {
        final AppState loaded = AppStatePersistence.load(directory);
        final AppSettings defaults = Defaults.appSettings();

        assertTrue(loaded.projects().isEmpty(), "missing projects should be empty");
        assertTrue(loaded.sessions().isEmpty(), "missing sessions should be empty");
        assertTrue(loaded.terminals().isEmpty(), "missing terminals should be empty");
        assertEquals(
                defaults.groupOrder(),
                loaded.appSettings().groupOrder(),
                "default group order should load");
        assertEquals(
                defaults.reviewPrompt(),
                loaded.appSettings().reviewPrompt(),
                "default review prompt should load");
        assertEquals(defaults.theme(), loaded.appSettings().theme(), "default theme should load");
        assertEquals(
                defaults.worktreeTemplate(),
                loaded.appSettings().worktreeTemplate(),
                "default worktree template should load");
    }

    @Test
    void persistsAndLoadsProjectsSessionsTerminalsAndSettings(@TempDir final Path directory)
            throws IOException, InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project("Demo", "/tmp/demo", null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(projectId, "Feature", "agent", "prompt", "/tmp/worktree"));
        final var terminalId = state.addTerminal(sessionId, new Terminal(sessionId, "Shell", "sh"));
        state.updateAppSettings(
                new AppSettings(
                        Defaults.appSettings().agents(),
                        Defaults.appSettings().groupOrder(),
                        "review",
                        "Dark",
                        Defaults.appSettings().tools(),
                        "custom/{sessionSlug}"));

        try (AppStatePersistence persistence = new AppStatePersistence(state, directory)) {
            persistence.persist();
        }

        assertTrue(Files.exists(directory.resolve("projects.json")), "projects should be written");
        assertTrue(Files.exists(directory.resolve(SETTINGS_FILE)), "settings should be written");
        assertTrue(
                Files.exists(
                        directory
                                .resolve("terminal-history")
                                .resolve(terminalId.value() + ".history")),
                "terminal history should be written");

        final AppState loaded = AppStatePersistence.load(directory);
        assertEquals("Demo", loaded.projects().get(projectId).name(), "project should load");
        assertEquals("Feature", loaded.sessions().get(sessionId).name(), "session should load");
        assertEquals("Shell", loaded.terminals().get(terminalId).title(), "terminal should load");
        assertEquals("Dark", loaded.appSettings().theme(), "settings should load");
    }

    @Test
    void invalidFilesFallBackToDefaults(@TempDir final Path directory) throws IOException {
        Files.writeString(directory.resolve("projects.json"), "not json");

        final AppState loaded = AppStatePersistence.load(directory);

        assertTrue(loaded.projects().isEmpty(), "invalid projects should use defaults");
        assertTrue(loaded.sessions().isEmpty(), "invalid sessions should use defaults");
        assertTrue(loaded.terminals().isEmpty(), "invalid terminals should use defaults");
        assertFalse(
                loaded.appSettings().worktreeTemplate().isBlank(),
                "default worktree template should be present");
    }

    @Test
    void invalidSettingsFallBackToDefaults(@TempDir final Path directory) throws IOException {
        Files.writeString(directory.resolve(SETTINGS_FILE), "not json");

        final AppState loaded = AppStatePersistence.load(directory);

        assertEquals(
                Defaults.appSettings().theme(),
                loaded.appSettings().theme(),
                "invalid settings should use the default theme");
        assertEquals(
                Defaults.appSettings().worktreeTemplate(),
                loaded.appSettings().worktreeTemplate(),
                "invalid settings should use the default worktree template");
    }

    @Test
    void persistsTerminalAddAndRemoveEvents(@TempDir final Path directory)
            throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project("Demo", "/tmp/demo", null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(projectId, "Feature", "agent", "prompt", "/tmp/worktree"));
        final var terminalId = state.addTerminal(sessionId, new Terminal(sessionId, "Shell", "sh"));
        final Path history =
                directory.resolve("terminal-history").resolve(terminalId.value() + ".history");

        try (AppStatePersistence persistence = new AppStatePersistence(state, directory)) {
            persistence.persist();
        }
        assertTrue(Files.exists(history), "terminal add should create history");

        state.removeTerminal(terminalId);
        try (AppStatePersistence persistence = new AppStatePersistence(state, directory)) {
            persistence.persist();
        }
        assertFalse(Files.exists(history), "terminal removal should delete history");
    }

    @Test
    void persistSnapshotsDirectlyWhenCalledOnEdt(@TempDir final Path directory)
            throws InterruptedException, java.lang.reflect.InvocationTargetException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.updateAppSettings(Defaults.appSettings());

        try (AppStatePersistence persistence = new AppStatePersistence(state, directory)) {
            SwingUtilities.invokeAndWait(persistence::persist);
        }

        assertTrue(Files.exists(directory.resolve("settings.json")), "EDT snapshot should persist");
        assertNull(state.snapshotForPersistence(), "EDT snapshot should clear pending updates");
    }

    @Test
    void restoresUpdatesAfterPersistenceWriteFailure(@TempDir final Path directory)
            throws IOException, InterruptedException, java.lang.reflect.InvocationTargetException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.updateAppSettings(Defaults.appSettings());
        final Path unusableDirectory = directory.resolve("not-a-directory");
        Files.writeString(unusableDirectory, "blocker");

        try (AppStatePersistence persistence = new AppStatePersistence(state, unusableDirectory)) {
            persistence.persist();
        }
        SwingUtilities.invokeAndWait(() -> {});

        assertTrue(
                state.snapshotForPersistence().appSettingsUpdated(),
                "failed write should restore updates");
    }

    @Test
    void successfulWritesRemoveTheirTemporaryFiles(@TempDir final Path directory)
            throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.updateAppSettings(Defaults.appSettings());

        try (AppStatePersistence persistence = new AppStatePersistence(state, directory)) {
            persistence.persist();
        }

        assertFalse(
                Files.exists(directory.resolve(SETTINGS_FILE + ".tmp")),
                "temporary settings file should be removed");
        assertTrue(Files.exists(directory.resolve(SETTINGS_FILE)), "settings file should remain");
    }
}
