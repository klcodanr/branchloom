package com.jagent.desktop.services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Guesses a dependency installation command from the files at a project root. */
public final class InstallCommandGuesser {
    private InstallCommandGuesser() {}

    public static Optional<String> guess(final Path projectPath) {
        if (hasAnyWithExtension(projectPath, ".csproj", ".sln")) {
            return Optional.of("dotnet restore");
        }
        if (has(projectPath, "Podfile")) {
            return Optional.of("pod install");
        }
        final List<Rule> rules =
                List.of(
                        new Rule(new String[] {"bun.lockb", "bun.lock"}, "bun install"),
                        new Rule(new String[] {"pnpm-lock.yaml"}, "pnpm install"),
                        new Rule(new String[] {"yarn.lock"}, "yarn install"),
                        new Rule(new String[] {"package-lock.json", "package.json"}, "npm install"),
                        new Rule(
                                new String[] {"requirements.txt"},
                                "pip install -r requirements.txt"),
                        new Rule(new String[] {"pyproject.toml"}, "poetry install"),
                        new Rule(new String[] {"Pipfile"}, "pipenv install"),
                        new Rule(
                                new String[] {"environment.yml"},
                                "conda env create -f environment.yml"),
                        new Rule(new String[] {"pom.xml"}, "mvn install"),
                        new Rule(new String[] {"build.gradle", "build.gradle.kts"}, "gradle build"),
                        new Rule(new String[] {"Gemfile"}, "bundle install"),
                        new Rule(new String[] {"composer.json"}, "composer install"),
                        new Rule(new String[] {"Cargo.toml"}, "cargo build"),
                        new Rule(new String[] {"go.mod"}, "go mod download"));
        for (final Rule rule : rules) {
            if (hasAny(projectPath, rule.fileNames())) {
                return Optional.of(
                        "gradle build".equals(rule.command()) && has(projectPath, "gradlew")
                                ? "./gradlew build"
                                : rule.command());
            }
        }
        return Optional.empty();
    }

    private static boolean has(final Path projectPath, final String fileName) {
        return Files.isRegularFile(projectPath.resolve(fileName));
    }

    private static boolean hasAny(final Path projectPath, final String... fileNames) {
        for (final String fileName : fileNames) {
            if (has(projectPath, fileName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyWithExtension(final Path projectPath, final String... extensions) {
        try (var files = Files.list(projectPath)) {
            return files.filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .filter(java.util.Objects::nonNull)
                    .map(Path::toString)
                    .anyMatch(
                            fileName ->
                                    java.util.Arrays.stream(extensions)
                                            .anyMatch(fileName::endsWith));
        } catch (java.io.IOException exception) {
            return false;
        }
    }

    private record Rule(String[] fileNames, String command) {}
}
