package com.jagent.desktop.services;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes the optional project context supplied to a new agent session. */
public final class AgentContext {
    private AgentContext() {}

    public static void write(final Project project, final Session session) throws IOException {
        final String configuredContextPath = project.agentContextPath();
        if (configuredContextPath == null || configuredContextPath.isBlank()) {
            return;
        }
        final String configuredPath =
                Template.expand(configuredContextPath, project, session, false);
        final Path path = Path.of(configuredPath);
        final Path contextPath =
                path.isAbsolute()
                        ? path
                        : Path.of(session.worktreePath()).resolve(path).normalize();
        final Path conflictingParent = contextPath.getParent();
        final Path target =
                conflictingParent != null && Files.isRegularFile(conflictingParent)
                        ? conflictingParent
                        : contextPath;
        final Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, content(project, session));
    }

    private static String content(final Project project, final Session session) {
        final String githubHost = project.githubHost();
        final String githubUser = project.githubUser();
        final String additionalText = project.agentContextText();
        final StringBuilder content =
                new StringBuilder(256)
                        .append("# Agent context\n\n## Project\n\n- Name: ")
                        .append(project.name())
                        .append("\n- Repository: ")
                        .append(project.path())
                        .append("\n- Worktree: ")
                        .append(session.worktreePath());
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

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }
}
