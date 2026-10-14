package com.jagent.desktop.services.git;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.test.TestGitRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitRepositoryTest {
    @Test
    void cloneRepositoryUsesNativeGitAndReturnsClonedDirectory(@TempDir final Path tempDir)
            throws IOException, InterruptedException {
        final Path source = tempDir.resolve("source");
        final Path cloneParent = tempDir.resolve("clones");
        final Path destination = cloneParent.resolve("source");
        Files.createDirectories(source);
        Files.createDirectories(cloneParent);
        TestGitRepository.initialize(source);

        final Path cloned = GitRepository.cloneRepository(source.toString(), destination);

        assertTrue(Files.isDirectory(cloned), "clone should create the repository directory");
        assertTrue(
                Files.exists(cloned.resolve("tracked.txt")),
                "clone should contain repository files");
    }
}
