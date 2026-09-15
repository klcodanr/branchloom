package com.jagent.desktop.services;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes the optional project context supplied to a new agent session. */
public final class AgentContext {
    private AgentContext() {}

    public static void write(
            final Project project, final Session session, final String globalContextPath)
            throws IOException {
        final Path target = path(project, session, globalContextPath);
        if (target == null) {
            return;
        }
        final Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, generatedContent(project, session));
    }

    public static String generatedContent(final Project project, final Session session) {
        final String githubHost = project.githubHost();
        final String githubUser = project.githubUser();
        final String additionalText = project.agentContextText();
        final StringBuilder content =
                new StringBuilder(384)
                        .append("# Agent context\n\n## Project\n\n- Name: ")
                        .append(project.name())
                        .append("\n- Repository: ")
                        .append(project.path())
                        .append("\n- Worktree: ")
                        .append(session.worktreePath())
                        .append(
                                "\n\n## Working notes\n\n"
                                        + "- Read this file at the start of each session before making changes.\n"
                                        + "- Keep ongoing notes in this file as you work so future sessions share the same context.\n");
        if (hasText(githubHost) || hasText(githubUser)) {
            content.append("\n\n## GitHub CLI\n\n");
            if (hasText(githubHost)) {
                content.append("- Host: ").append(githubHost).append('\n');
            }
            if (hasText(githubUser)) {
                content.append("- User: ").append(githubUser).append('\n');
            }
        }
        if (hasText(additionalText)) {
            content.append("\n\n## Additional context\n\n").append(additionalText).append('\n');
        }
        return content.toString();
    }

    public static String read(
            final Project project, final Session session, final String globalContextPath)
            throws IOException {
        final Path target = path(project, session, globalContextPath);
        if (target == null) {
            return "";
        }
        if (!Files.exists(target)) {
            return generatedContent(project, session);
        }
        return Files.readString(target);
    }

    public static void save(
            final Project project,
            final Session session,
            final String globalContextPath,
            final String content)
            throws IOException {
        final Path target = path(project, session, globalContextPath);
        if (target == null) {
            return;
        }
        final Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, content);
    }

    public static Path path(
            final Project project, final Session session, final String globalContextPath) {
        return targetPath(project, session, globalContextPath);
    }

    private static Path targetPath(
            final Project project, final Session session, final String globalContextPath) {
        final String configuredContextPath = configuredContextPath(project, globalContextPath);
        if (configuredContextPath == null || configuredContextPath.isBlank()) {
            return null;
        }
        final String configuredPath =
                Template.expand(configuredContextPath, project, session, false);
        final Path path = Path.of(configuredPath);
        final Path contextPath =
                path.isAbsolute()
                        ? path
                        : Path.of(session.worktreePath()).resolve(path).normalize();
        final Path conflictingParent = contextPath.getParent();
        if (conflictingParent != null && Files.isRegularFile(conflictingParent)) {
            return conflictingParent;
        }
        return contextPath;
    }

    private static String configuredContextPath(
            final Project project, final String globalContextPath) {
        final String projectPath = project.agentContextPath();
        if (hasText(projectPath)) {
            return projectPath;
        }
        return hasText(globalContextPath) ? globalContextPath : null;
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }
}
