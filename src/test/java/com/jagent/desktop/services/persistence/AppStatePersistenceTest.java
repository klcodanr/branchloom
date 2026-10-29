package com.jagent.desktop.services.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.jagent.desktop.models.Agent;
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
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppStatePersistenceTest {
    private static final String SETTINGS_FILE = "settings.json";
    private static final String AGENT_COMMAND = "agent";
    private static final String INVALID_JSON = "not json";
    private static final String PROJECT_NAME = "Demo";
    private static final String THEME = "Dark";
    private static final String CONTEXT_PATH = ".branchloom/context.md";
    private static final String DEMO_PATH = "/tmp/demo";
    private static final String PROJECTS_FILE = "projects.json";

    @Test
    void missingFilesLoadDefaultSettingsAndEmptyState(@TempDir final Path directory) {
        final AppState loaded = AppStatePersistence.load(directory);
        final AppSettings defaults = Defaults.appSettings();

        assertTrue(
                Files.exists(directory.resolve(SETTINGS_FILE)),
                "missing settings should be initialized");
        assertTrue(
                Files.exists(directory.resolve("settings.schema.json")),
                "settings schema should be initialized");
        assertTrue(loaded.projects().isEmpty(), "missing projects should be empty");
        assertTrue(loaded.sessions().isEmpty(), "missing sessions should be empty");
        assertTrue(loaded.terminals().isEmpty(), "missing terminals should be empty");
        assertEquals(
                defaults.reviewPrompt(),
                loaded.appSettings().reviewPrompt(),
                "default review prompt should load");
        assertEquals(defaults.theme(), loaded.appSettings().theme(), "default theme should load");
        assertEquals(
                defaults.worktreeTemplate(),
                loaded.appSettings().worktreeTemplate(),
                "default worktree template should load");
        assertEquals(
                defaults.agentContextPath(),
                loaded.appSettings().agentContextPath(),
                "default context path should load");
        assertEquals(
                defaults.pullRequestFilters(),
                loaded.appSettings().pullRequestFilters(),
                "default pull request filters should load");
    }

    @Test
    void persistsAndLoadsProjectsSessionsTerminalsAndSettings(@TempDir final Path directory)
            throws IOException, InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(Project.create(PROJECT_NAME, DEMO_PATH, null));
        final var sessionId =
                state.addSession(
                        projectId,
                        Session.create(
                                projectId, "Feature", AGENT_COMMAND, "prompt", "/tmp/worktree"));
        final var terminalId =
                state.addTerminal(sessionId, Terminal.create(sessionId, "Shell", "sh"));
        state.updateAppSettings(
                new AppSettings(
                        List.of(new Agent("Test agent", "agent {prompt}", AGENT_COMMAND)),
                        "review",
                        THEME,
                        Defaults.appSettings().tools(),
                        "custom/{sessionSlug}",
                        CONTEXT_PATH));
        Files.createDirectories(directory);
        Files.writeString(
                directory.resolve(SETTINGS_FILE),
                PersistenceSupport.JSON.toJson(state.appSettings()));
        try (AppStatePersistence persistence = new AppStatePersistence(state, directory)) {
            persistence.persist();
        }

        assertTrue(Files.exists(directory.resolve(PROJECTS_FILE)), "projects should be written");
        assertTrue(
                Files.exists(directory.resolve(SETTINGS_FILE)),
                "existing settings should remain available");
        assertTrue(
                Files.exists(
                        directory
                                .resolve("terminal-history")
                                .resolve(terminalId.value() + ".history")),
                "terminal history should be written");

        final AppState loaded = AppStatePersistence.load(directory);
        assertEquals(PROJECT_NAME, loaded.projects().get(projectId).name(), "project should load");
        assertEquals("Feature", loaded.sessions().get(sessionId).name(), "session should load");
        assertEquals("Shell", loaded.terminals().get(terminalId).title(), "terminal should load");
        assertEquals(THEME, loaded.appSettings().theme(), "settings should load");
        assertEquals(
                "Test agent",
                loaded.appSettings().agents().getFirst().name,
                "agent name should load");
        assertEquals(
                "agent {prompt}",
                loaded.appSettings().agents().getFirst().newSessionCommand,
                "agent new-session command should load");
        assertEquals(
                AGENT_COMMAND,
                loaded.appSettings().agents().getFirst().openCommand,
                "agent open command should load");
        assertEquals(
                CONTEXT_PATH, loaded.appSettings().agentContextPath(), "context path should load");
        assertEquals(
                state.appSettings().pullRequestFilters(),
                loaded.appSettings().pullRequestFilters(),
                "configured pull request filters should load");
    }

    @Test
    void invalidFilesFallBackToDefaults(@TempDir final Path directory) throws IOException {
        Files.writeString(directory.resolve(PROJECTS_FILE), INVALID_JSON);

        final AppState loaded = AppStatePersistence.load(directory);

        assertTrue(loaded.projects().isEmpty(), "invalid projects should use defaults");
        assertTrue(loaded.sessions().isEmpty(), "invalid sessions should use defaults");
        assertTrue(loaded.terminals().isEmpty(), "invalid terminals should use defaults");
        assertFalse(
                loaded.appSettings().worktreeTemplate().isBlank(),
                "default worktree template should be present");
    }

    @Test
    void invalidSettingsFailToLoad(@TempDir final Path directory) throws IOException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(Project.create(PROJECT_NAME, DEMO_PATH, null));

        try (AppStatePersistence persistence = new AppStatePersistence(state, directory)) {
            persistence.persist();
        }
        Files.writeString(directory.resolve(SETTINGS_FILE), INVALID_JSON);

        final IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> AppStatePersistence.load(directory));

        assertTrue(
                exception.getMessage().contains("could not load settings.json"),
                "invalid settings should produce a descriptive failure");
        assertTrue(
                Files.exists(directory.resolve(PROJECTS_FILE)),
                "project persistence should remain available for recovery");
    }

    @Test
    void invalidProjectsDoNotDiscardSettings(@TempDir final Path directory) throws IOException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.updateAppSettings(
                new AppSettings(
                        Defaults.appSettings().agents(),
                        "review",
                        THEME,
                        Defaults.appSettings().tools(),
                        "custom/{sessionSlug}",
                        CONTEXT_PATH));

        try (AppStatePersistence persistence = new AppStatePersistence(state, directory)) {
            persistence.persist();
        }
        Files.writeString(
                directory.resolve(SETTINGS_FILE),
                PersistenceSupport.JSON.toJson(state.appSettings()));
        Files.writeString(directory.resolve(PROJECTS_FILE), INVALID_JSON);

        final AppState loaded = AppStatePersistence.load(directory);

        assertTrue(loaded.projects().isEmpty(), "invalid projects should use the empty default");
        assertEquals(THEME, loaded.appSettings().theme(), "settings should still load");
    }

    @Test
    void invalidSettingsFailInitialLoad(@TempDir final Path directory) throws IOException {
        Files.writeString(directory.resolve(SETTINGS_FILE), INVALID_JSON);

        final IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> AppStatePersistence.load(directory));

        assertTrue(
                exception.getMessage().contains("could not load settings.json"),
                "invalid settings should report an error");
        assertEquals(
                INVALID_JSON,
                Files.readString(directory.resolve(SETTINGS_FILE)),
                "invalid settings should remain untouched");
    }

    @Test
    void legacySettingsFieldsAreIgnored(@TempDir final Path directory) throws IOException {
        final String legacySettingsJson =
                """
                {
                  "agents": [{"name":"Demo Agent","newSessionCommand":"agent {prompt}","openCommand":"agent"}],
                  "reviewPrompt": "review",
                  "theme": "Dark",
                  "tools": [],
                  "worktreeTemplate": "custom/{sessionSlug}",
                  "agentContextPath": ".branchloom/context.md",
                  "reviewPlanEnabled": true,
                  "reviewPlanCommand": "legacy-command",
                  "reviewPlanPrompt": "plan"
                }
                """;
        Files.writeString(directory.resolve(SETTINGS_FILE), legacySettingsJson);

        final AppState loaded = AppStatePersistence.load(directory);

        assertEquals("Dark", loaded.appSettings().theme(), "theme should be read from settings");
        assertEquals(
                "custom/{sessionSlug}",
                loaded.appSettings().worktreeTemplate(),
                "worktree template should be read from settings");
        assertEquals(
                CONTEXT_PATH,
                loaded.appSettings().agentContextPath(),
                "context path should be read from settings");
        assertEquals(1, loaded.appSettings().agents().size(), "agent should load from settings");
    }

    @Test
    void persistsTerminalAddAndRemoveEvents(@TempDir final Path directory)
            throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(Project.create("Demo", DEMO_PATH, null));
        final var sessionId =
                state.addSession(
                        projectId,
                        Session.create(projectId, "Feature", "agent", "prompt", "/tmp/worktree"));
        final var terminalId =
                state.addTerminal(sessionId, Terminal.create(sessionId, "Shell", "sh"));
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
        state.addProject(Project.create(PROJECT_NAME, DEMO_PATH, null));

        try (AppStatePersistence persistence = new AppStatePersistence(state, directory)) {
            SwingUtilities.invokeAndWait(persistence::persist);
        }

        assertTrue(Files.exists(directory.resolve(PROJECTS_FILE)), "EDT snapshot should persist");
        assertNull(state.snapshotForPersistence(), "EDT snapshot should clear pending updates");
    }

    @Test
    void closesWithoutWaitingForPersistenceExecutorOnEdt(@TempDir final Path directory)
            throws InterruptedException, java.lang.reflect.InvocationTargetException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(Project.create(PROJECT_NAME, DEMO_PATH, null));
        try (AppStatePersistence persistence = new AppStatePersistence(state, directory)) {
            SwingUtilities.invokeAndWait(persistence::close);
        }

        assertTrue(
                Files.exists(directory.resolve(PROJECTS_FILE)),
                "closing on the EDT should persist projects");
    }

    @Test
    void restoresUpdatesAfterPersistenceWriteFailure(@TempDir final Path directory)
            throws IOException, InterruptedException, java.lang.reflect.InvocationTargetException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(Project.create(PROJECT_NAME, DEMO_PATH, null));
        final Path unusableDirectory = directory.resolve("not-a-directory");
        Files.writeString(unusableDirectory, "blocker");

        try (AppStatePersistence persistence = new AppStatePersistence(state, unusableDirectory)) {
            persistence.persist();
        }
        SwingUtilities.invokeAndWait(() -> {});

        assertTrue(
                state.snapshotForPersistence().projectsUpdated(),
                "failed write should restore updates");
    }

    @Test
    void successfulWritesRemoveTheirTemporaryFiles(@TempDir final Path directory)
            throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(Project.create(PROJECT_NAME, DEMO_PATH, null));

        try (AppStatePersistence persistence = new AppStatePersistence(state, directory)) {
            persistence.persist();
        }

        assertFalse(
                Files.exists(directory.resolve(PROJECTS_FILE + ".tmp")),
                "temporary projects file should be removed");
        assertTrue(Files.exists(directory.resolve(PROJECTS_FILE)), "projects file should remain");
    }
}
