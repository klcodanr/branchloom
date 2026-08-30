package com.jagent.desktop.services.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jagent.desktop.models.WindowState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns persistence for the application window geometry. */
public final class WindowStatePersistence implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(WindowStatePersistence.class.getName());
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            Path.of(System.getProperty("user.home"), ".branchloom", "windowState.json");
    private static final Path DIRECTORY = PATH.getParent();

    private WindowState state;

    public WindowStatePersistence() {
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
            Files.createDirectories(DIRECTORY);
            final Path temporary = PATH.resolveSibling(PATH.getFileName() + ".tmp");
            Files.writeString(temporary, JSON.toJson(state));
            Files.move(
                    temporary,
                    PATH,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            LOG.log(Level.WARNING, "Failed to persist window state", exception);
        }
    }

    private static WindowState load() {
        try {
            if (!Files.exists(PATH)) {
                return new WindowState();
            }
            return JSON.fromJson(Files.readString(PATH), WindowState.class);
        } catch (IOException | RuntimeException exception) {
            LOG.log(Level.WARNING, "Failed to load window state", exception);
            return new WindowState();
        }
    }
}
