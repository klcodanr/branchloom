package com.jagent.desktop.services.persistence;

import com.jagent.desktop.models.WindowState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns persistence for the application window geometry. */
public final class WindowStatePersistence extends PersistenceSupport implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(WindowStatePersistence.class.getName());

    private final Path path;
    private final Path directory;
    private WindowState state;

    public WindowStatePersistence() {
        this(DEFAULT_DIRECTORY);
    }

    public WindowStatePersistence(final Path directory) {
        super();
        this.path = directory.resolve("windowState.json");
        this.directory = directory;
        this.state = load();
    }

    public WindowState state() {
        return state;
    }

    public void update(final WindowState state) {
        this.state = state;
    }

    @Override
    public void close() {
        try {
            Files.createDirectories(directory);
            writeAtomically(path, state);
        } catch (IOException exception) {
            LOG.log(Level.WARNING, "Failed to persist window state", exception);
        }
    }

    private WindowState load() {
        try {
            if (!Files.exists(path)) {
                return new WindowState();
            }
            return JSON.fromJson(Files.readString(path), WindowState.class);
        } catch (IOException | RuntimeException exception) {
            LOG.log(Level.WARNING, "Failed to load window state", exception);
            return new WindowState();
        }
    }
}
