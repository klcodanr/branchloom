package com.jagent.desktop.services.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class PersistenceSupport {
    /* default */ static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    /* default */ static final Path DEFAULT_DIRECTORY =
            Path.of(System.getProperty("user.home"), ".branchloom");

    private PersistenceSupport() {}

    /* default */ static void writeAtomically(final Path path, final Object value)
            throws IOException {
        final Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, JSON.toJson(value));
        Files.move(
                temporary,
                path,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }
}
