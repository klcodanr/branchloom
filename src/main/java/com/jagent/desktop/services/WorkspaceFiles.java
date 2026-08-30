package com.jagent.desktop.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Reads workspace entries for the read-only workspace navigator. */
public final class WorkspaceFiles {
    private final Path root;

    public WorkspaceFiles(final Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public List<Path> children(final Path directory) throws IOException, InterruptedException {
        try (Stream<Path> entries = Files.list(directory)) {
            final List<Path> candidates = entries.toList();
            final Set<String> ignored = ignoredPaths(candidates);
            return candidates.stream()
                    .filter(path -> !".git".equals(fileName(path)))
                    .filter(path -> !ignored.contains(relativePath(path)))
                    .sorted(
                            Comparator.comparing((Path path) -> !Files.isDirectory(path))
                                    .thenComparing(this::fileName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    private Set<String> ignoredPaths(final List<Path> candidates)
            throws IOException, InterruptedException {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        final Process process =
                new ProcessBuilder("git", "check-ignore", "--stdin", "-z")
                        .directory(root.toFile())
                        .redirectErrorStream(true)
                        .start();
        try (var input = process.getOutputStream()) {
            for (final Path candidate : candidates) {
                input.write(relativePath(candidate).getBytes(StandardCharsets.UTF_8));
                input.write(0);
            }
        }
        final String output =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        final int exitCode = process.waitFor();
        if (exitCode == 1) {
            return Set.of();
        }
        if (exitCode != 0) {
            throw new IOException(output.trim());
        }
        return Stream.of(output.split("\\u0000", -1))
                .filter(path -> !path.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private String relativePath(final Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private String fileName(final Path path) {
        final Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }
}
