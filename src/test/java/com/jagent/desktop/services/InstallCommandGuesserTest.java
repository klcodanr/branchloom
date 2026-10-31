package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstallCommandGuesserTest {
    @Test
    void guessesSupportedCommands(@TempDir final Path directory) throws IOException {
        final String[][] cases = {
            {"package-lock.json", "npm install"},
            {"yarn.lock", "yarn install"},
            {"pnpm-lock.yaml", "pnpm install"},
            {"bun.lock", "bun install"},
            {"requirements.txt", "pip install -r requirements.txt"},
            {"pyproject.toml", "poetry install"},
            {"Pipfile", "pipenv install"},
            {"environment.yml", "conda env create -f environment.yml"},
            {"pom.xml", "mvn install"},
            {"build.gradle.kts", "gradle build"},
            {"Gemfile", "bundle install"},
            {"composer.json", "composer install"},
            {"Cargo.toml", "cargo build"},
            {"go.mod", "go mod download"},
            {"project.csproj", "dotnet restore"},
            {"Podfile", "pod install"}
        };

        for (final String[] testCase : cases) {
            Files.createFile(directory.resolve(testCase[0]));
            assertEquals(
                    Optional.of(testCase[1]),
                    InstallCommandGuesser.guess(directory),
                    "command for " + testCase[0]);
            Files.delete(directory.resolve(testCase[0]));
        }
    }

    @Test
    void prefersLockfilePackageManagerAndGradleWrapper(@TempDir final Path directory)
            throws IOException {
        Files.createFile(directory.resolve("package.json"));
        Files.createFile(directory.resolve("yarn.lock"));
        assertEquals(
                Optional.of("yarn install"),
                InstallCommandGuesser.guess(directory),
                "lockfile should select its package manager");

        Files.delete(directory.resolve("yarn.lock"));
        Files.delete(directory.resolve("package.json"));
        Files.createFile(directory.resolve("build.gradle"));
        Files.createFile(directory.resolve("gradlew"));
        assertEquals(
                Optional.of("./gradlew build"),
                InstallCommandGuesser.guess(directory),
                "Gradle wrapper should be preferred");
    }

    @Test
    void returnsEmptyWhenNoRecognizedFilesExist(@TempDir final Path directory) {
        assertTrue(
                InstallCommandGuesser.guess(directory).isEmpty(),
                "unrecognized project files should not produce a command");
    }
}
