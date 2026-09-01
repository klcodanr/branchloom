package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.test.TestGitRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceFilesTest {
    private static final String SRC = "src";

    @Test
    void childrenSortDirectoriesBeforeFilesAndExcludeGitIgnoredEntries(@TempDir final Path root)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(root);
        Files.createDirectory(root.resolve(SRC));
        Files.writeString(root.resolve("README.md"), "readme");
        Files.writeString(root.resolve(SRC).resolve("Main.java"), "main");
        Files.writeString(root.resolve("ignored.txt"), "ignored");
        Files.writeString(root.resolve(".gitignore"), "ignored.txt\n");

        final List<Path> children = new WorkspaceFiles(root).children(root);

        assertEquals(
                List.of(
                        root.resolve(SRC),
                        root.resolve(".gitignore"),
                        root.resolve("README.md"),
                        root.resolve("tracked.txt")),
                children,
                "directories should be first and ignored entries should be absent");
        assertFalse(
                children.stream().anyMatch(path -> path.equals(root.resolve(".git"))),
                "the Git metadata directory should be excluded");
    }

    @Test
    void emptyDirectoryHasNoChildren(@TempDir final Path root)
            throws IOException, InterruptedException {
        assertTrue(
                new WorkspaceFiles(root).children(root).isEmpty(),
                "an empty directory should have no workspace children");
    }

    @Test
    void gitIgnoreFailureIsReported(@TempDir final Path root) throws IOException {
        Files.writeString(root.resolve("file.txt"), "content");

        final IOException failure =
                org.junit.jupiter.api.Assertions.assertThrows(
                        IOException.class,
                        () -> new WorkspaceFiles(root).children(root),
                        "git check-ignore failures should be reported");

        assertTrue(
                failure.getMessage() != null && !failure.getMessage().isBlank(),
                "git failure should include command output");
    }

    @Test
    void ignoresNestedPathsAndSupportsSpacesInNames(@TempDir final Path root)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(root);
        Files.createDirectories(root.resolve("src/nested folder"));
        Files.writeString(root.resolve(".gitignore"), "src/nested folder/ignored.txt\n");
        Files.writeString(root.resolve("src/nested folder/ignored.txt"), "ignored");
        Files.writeString(root.resolve("src/nested folder/kept file.txt"), "kept");

        assertEquals(
                List.of(root.resolve(SRC + "/nested folder")),
                new WorkspaceFiles(root).children(root.resolve(SRC)),
                "nested directories should be retained");
        assertEquals(
                List.of(root.resolve(SRC + "/nested folder/kept file.txt")),
                new WorkspaceFiles(root).children(root.resolve(SRC + "/nested folder")),
                "ignored nested files should be excluded");
    }
}
