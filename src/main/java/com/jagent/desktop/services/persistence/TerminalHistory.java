package com.jagent.desktop.services.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class TerminalHistory {
    private static final Path DIRECTORY =
            Path.of(System.getProperty("user.home"), ".branchloom", "terminal-history");

    private TerminalHistory() {}

    public static String createPath() throws IOException {
        Files.createDirectories(DIRECTORY);
        return DIRECTORY.resolve(UUID.randomUUID() + ".history").toString();
    }

    public static void ensureParent(final String path) throws IOException {
        final Path parent = Path.of(path).toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    public static void delete(final String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (IOException ignored) {
            // History cleanup should not prevent a terminal from closing.
        }
    }
}
