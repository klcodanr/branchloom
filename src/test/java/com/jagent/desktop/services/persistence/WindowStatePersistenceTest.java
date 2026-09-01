package com.jagent.desktop.services.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.WindowState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowStatePersistenceTest {
    @Test
    void persistsWithinInjectedDirectory(@TempDir final Path directory) {
        final WindowState state = new WindowState();
        state.windowX = 10;
        state.windowWidth = 800;

        try (WindowStatePersistence persistence = new WindowStatePersistence(directory)) {
            persistence.update(state);
        }

        final Path path = directory.resolve("windowState.json");
        assertTrue(Files.exists(path), "window state should be written");
        try (WindowStatePersistence persistence = new WindowStatePersistence(directory)) {
            assertEquals(10, persistence.state().windowX, "window x should be restored");
            assertEquals(800, persistence.state().windowWidth, "window width should be restored");
        }
    }

    @Test
    void invalidStateFallsBackToDefaults(@TempDir final Path directory) throws IOException {
        Files.writeString(directory.resolve("windowState.json"), "not valid json");

        try (WindowStatePersistence persistence = new WindowStatePersistence(directory)) {
            assertEquals(0, persistence.state().windowX, "assertion values should match");
            assertEquals(0, persistence.state().windowWidth, "assertion values should match");
        }
    }
}
